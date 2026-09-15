package io.github.paulneja.archdroid.core.chroot

import io.github.paulneja.archdroid.core.util.ShellQuote.quote
import io.github.paulneja.archdroid.data.model.DevMode
import io.github.paulneja.archdroid.data.model.MountConfig

/**
 * Pure builder for the mount/umount command lists. Kept free of any root or Android
 * dependency so the exact commands that run as root can be unit tested.
 */
object MountPlan {

    /** name, major, minor -- the character devices a login shell and pacman rely on. */
    private val DEV_NODES = listOf(
        Triple("null", 1, 3),
        Triple("zero", 1, 5),
        Triple("full", 1, 7),
        Triple("random", 1, 8),
        Triple("urandom", 1, 9),
        Triple("tty", 5, 0),
    )

    /** Symlinks into /proc that a bare tmpfs /dev would otherwise be missing. */
    private val DEV_LINKS = listOf(
        "fd" to "/proc/self/fd",
        "stdin" to "/proc/self/fd/0",
        "stdout" to "/proc/self/fd/1",
        "stderr" to "/proc/self/fd/2",
    )

    fun mountCommands(cfg: MountConfig, chrootDir: String): List<String> {
        val c = chrootDir
        val cmds = mutableListOf("mkdir -p $c/proc $c/sys $c/dev $c/mnt")

        if (cfg.proc) cmds += "grep -q ' $c/proc ' /proc/mounts || mount -t proc proc $c/proc"
        if (cfg.sys) cmds += "grep -q ' $c/sys ' /proc/mounts || mount -t sysfs sysfs $c/sys"
        if (cfg.dev) {
            when (cfg.devMode) {
                DevMode.BIND ->
                    cmds += "grep -q ' $c/dev ' /proc/mounts || mount --bind /dev $c/dev"

                DevMode.PRIVATE -> {
                    cmds += "grep -q ' $c/dev ' /proc/mounts || " +
                        "mount -t tmpfs -o mode=755,size=16M tmpfs $c/dev"
                    // A private /dev starts empty, so the nodes a shell expects have to be
                    // made by hand -- after the tmpfs is in place, or they land in the
                    // rootfs and get hidden the moment it mounts.
                    DEV_NODES.forEach { (name, major, minor) ->
                        cmds += "[ -e $c/dev/$name ] || mknod -m 666 $c/dev/$name c $major $minor"
                    }
                    // mkinitcpio decides whether /dev is mounted by testing for /dev/fd,
                    // and shell redirections reach for /dev/stdin and its siblings. A bare
                    // tmpfs has none of them; /proc is already mounted by this point.
                    DEV_LINKS.forEach { (name, target) ->
                        cmds += "ln -sf $target $c/dev/$name"
                    }
                }
            }
        }

        // Only now: binding /dev over $c/dev hides anything created under it beforehand,
        // so these two have to be made once the bind is in place or the mounts below
        // target paths that do not exist. Because the bind points at the real /dev, the
        // mkdir lands there too -- Android ships no /dev/shm and this creates it. That is
        // what every chroot script does, and /dev is tmpfs, so a reboot undoes it.
        if (cfg.devPts) {
            cmds += "mkdir -p $c/dev/pts"
            cmds += "grep -q ' $c/dev/pts ' /proc/mounts || mount -t devpts devpts $c/dev/pts"
            if (cfg.devMode == DevMode.PRIVATE) {
                // Android's /dev ships a ptmx; a private one has to borrow the devpts copy.
                cmds += "[ -e $c/dev/ptmx ] || ln -s pts/ptmx $c/dev/ptmx"
            }
        }
        if (cfg.shm) {
            cmds += "mkdir -p $c/dev/shm"
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

    /** The mount points this config is supposed to bring up, outermost first. */
    fun expectedMountPoints(cfg: MountConfig, chrootDir: String): List<String> {
        val c = chrootDir
        val points = mutableListOf<String>()
        if (cfg.proc) points += "$c/proc"
        if (cfg.sys) points += "$c/sys"
        if (cfg.dev) points += "$c/dev"
        if (cfg.devPts) points += "$c/dev/pts"
        if (cfg.shm) points += "$c/dev/shm"
        if (cfg.bindSharedStorage) points += c + cfg.sharedStorageTarget
        return points
    }

    /**
     * Which of the expected mount points are not in /proc/mounts. Checking the kernel
     * beats trusting an exit code: the shell reports only the last command it ran, so a
     * mount that failed in the middle would otherwise pass unnoticed.
     */
    fun missingMounts(
        procMounts: List<String>,
        cfg: MountConfig,
        chrootDir: String,
    ): List<String> {
        val live = procMounts.mapNotNull { line ->
            line.split(' ').getOrNull(1)?.takeIf { it.isNotBlank() }?.let { decodeOctal(it) }
        }.toSet()
        return expectedMountPoints(cfg, chrootDir).filterNot { it in live }
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
