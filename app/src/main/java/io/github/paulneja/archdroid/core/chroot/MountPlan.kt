package io.github.paulneja.archdroid.core.chroot

import io.github.paulneja.archdroid.core.util.ShellQuote.quote
import io.github.paulneja.archdroid.data.model.MountConfig

/**
 * Pure builder for the mount/umount command lists. Kept free of any root or Android
 * dependency so the exact commands that run as root can be unit tested.
 */
object MountPlan {

    fun mountCommands(cfg: MountConfig, chrootDir: String): List<String> {
        val c = chrootDir
        val cmds = mutableListOf("mkdir -p $c/proc $c/sys $c/dev $c/dev/pts $c/dev/shm $c/mnt")

        if (cfg.proc) cmds += "grep -q ' $c/proc ' /proc/mounts || mount -t proc proc $c/proc"
        if (cfg.sys) cmds += "grep -q ' $c/sys ' /proc/mounts || mount -t sysfs sysfs $c/sys"
        if (cfg.dev) cmds += "grep -q ' $c/dev ' /proc/mounts || mount --bind /dev $c/dev"
        if (cfg.devPts) cmds += "grep -q ' $c/dev/pts ' /proc/mounts || mount -t devpts devpts $c/dev/pts"
        if (cfg.shm) {
            cmds += "grep -q ' $c/dev/shm ' /proc/mounts || " +
                "mount -t tmpfs -o size=${cfg.shmSizeMb}M tmpfs $c/dev/shm"
        }

        if (cfg.bindSharedStorage) {
            val src = quote(cfg.sharedStorageSource)
            val dst = quote(c + cfg.sharedStorageTarget)
            cmds += "mkdir -p $dst"
            cmds += "grep -q \" ${c + cfg.sharedStorageTarget} \" /proc/mounts || mount --bind $src $dst"
            if (cfg.sharedStorageReadOnly) {
                cmds += "mount -o remount,ro,bind $dst 2>/dev/null || true"
            }
        }
        return cmds
    }

    /** Deepest mount points first, so nothing stays busy behind a parent. */
    fun unmountCommands(cfg: MountConfig, chrootDir: String): List<String> {
        val c = chrootDir
        val points = mutableListOf<String>()
        if (cfg.bindSharedStorage) points += c + cfg.sharedStorageTarget
        points += listOf("$c/dev/shm", "$c/dev/pts", "$c/dev", "$c/sys", "$c/proc")
        return points
            .sortedByDescending { it.count { ch -> ch == '/' } }
            .map { "umount ${quote(it)} 2>/dev/null || umount -l ${quote(it)} 2>/dev/null || true" }
    }

    /**
     * Mount points from /proc/mounts that sit at or below [chrootDir]. A non-empty result
     * means the rootfs must not be deleted — an `rm -rf` would follow a live bind mount out
     * of the chroot and into the real filesystem it points at.
     */
    fun residualMounts(procMounts: List<String>, chrootDir: String): List<String> {
        val root = chrootDir.trimEnd('/')
        return procMounts.mapNotNull { line ->
            val point = line.split(' ').getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val decoded = decodeOctal(point)
            if (decoded == root || decoded.startsWith("$root/")) decoded else null
        }
    }

    private fun decodeOctal(value: String): String =
        Regex("""\\(\d{3})""").replace(value) { m -> m.groupValues[1].toInt(8).toChar().toString() }
}
