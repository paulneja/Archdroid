package io.github.paulneja.archdroid.core.chroot

import io.github.paulneja.archdroid.core.util.ShellQuote.quote
import io.github.paulneja.archdroid.data.model.DevMode
import io.github.paulneja.archdroid.data.model.MountConfig

object MountPlan {

    private val DEV_NODES = listOf(
        Triple("null", 1, 3),
        Triple("zero", 1, 5),
        Triple("full", 1, 7),
        Triple("random", 1, 8),
        Triple("urandom", 1, 9),
        Triple("tty", 5, 0),
    )

    private val DEV_LINKS = listOf(
        "fd" to "/proc/self/fd",
        "stdin" to "/proc/self/fd/0",
        "stdout" to "/proc/self/fd/1",
        "stderr" to "/proc/self/fd/2",
    )

    fun mountCommands(cfg: MountConfig, chrootDir: String): List<String> {
        val c = chrootDir
        // The self-bind must come first and allows suid without remounting /data.
        val cmds = mutableListOf(
            "grep -q ' $c ' /proc/mounts || mount --bind $c $c",
            "mount -o remount,suid,exec,dev $c",
            "mkdir -p $c/proc $c/sys $c/dev $c/mnt",
        )

        if (cfg.run) {
            cmds += "mkdir -p $c/run"
            cmds += "grep -q ' $c/run ' /proc/mounts || mount -t tmpfs -o mode=755,nosuid,nodev,size=128M tmpfs $c/run"
        }
        if (cfg.proc) cmds += "grep -q ' $c/proc ' /proc/mounts || mount -t proc proc $c/proc"
        if (cfg.sys) cmds += "grep -q ' $c/sys ' /proc/mounts || mount -t sysfs sysfs $c/sys"
        if (cfg.dev) {
            when (cfg.devMode) {
                DevMode.BIND ->
                    cmds += "grep -q ' $c/dev ' /proc/mounts || mount --bind /dev $c/dev"

                DevMode.PRIVATE -> {
                    cmds += "grep -q ' $c/dev ' /proc/mounts || " +
                        "mount -t tmpfs -o mode=755,size=16M tmpfs $c/dev"
                    DEV_NODES.forEach { (name, major, minor) ->
                        cmds += "[ -e $c/dev/$name ] || mknod -m 666 $c/dev/$name c $major $minor"
                    }
                    DEV_LINKS.forEach { (name, target) ->
                        cmds += "ln -sf $target $c/dev/$name"
                    }
                }
            }
        }

        // Create nested mount points only after /dev is mounted.
        if (cfg.devPts) {
            // Android's devpts: the terminal's pty lives there.
            cmds += "mkdir -p $c/dev/pts"
            cmds += "grep -q ' $c/dev/pts ' /proc/mounts || mount --bind /dev/pts $c/dev/pts"
            if (cfg.devMode == DevMode.PRIVATE) {
                // Android's pts/ptmx is mode 000, so a real node is needed for ordinary users.
                cmds += "[ -c $c/dev/ptmx ] || { rm -f $c/dev/ptmx; mknod -m 666 $c/dev/ptmx c 5 2; }"
            }
        }
        if (cfg.shm) {
            cmds += "mkdir -p $c/dev/shm"
            cmds += "grep -q ' $c/dev/shm ' /proc/mounts || " +
                "mount -t tmpfs -o size=${cfg.shmSizeMb}M tmpfs $c/dev/shm"
        }

        if (cfg.bindSharedStorage) {
            val src = quote(cfg.sharedStorageSource)
            val point = c + cfg.sharedStorageTarget
            val dst = quote(point)
            cmds += "mkdir -p $dst"
            cmds += "grep -qF ${quote(" $point ")} /proc/mounts || mount --bind $src $dst"
            cmds += "mount -o remount,${if (cfg.sharedStorageReadOnly) "ro" else "rw"},bind $dst"
        }
        return cmds
    }

    fun expectedMountPoints(cfg: MountConfig, chrootDir: String): List<String> {
        val c = chrootDir
        val points = mutableListOf(c)
        if (cfg.run) points += "$c/run"
        if (cfg.proc) points += "$c/proc"
        if (cfg.sys) points += "$c/sys"
        if (cfg.dev) points += "$c/dev"
        if (cfg.devPts) points += "$c/dev/pts"
        if (cfg.shm) points += "$c/dev/shm"
        if (cfg.bindSharedStorage) points += c + cfg.sharedStorageTarget
        return points
    }

    fun missingMounts(
        procMounts: List<String>,
        cfg: MountConfig,
        chrootDir: String,
    ): List<String> {
        val live = procMounts.mapNotNull { line ->
            val fields = line.split(' ')
            val point = fields.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            decodeOctal(point) to fields.getOrNull(3).orEmpty().split(',')
        }
        val sharedPoint = chrootDir + cfg.sharedStorageTarget
        return expectedMountPoints(cfg, chrootDir).filterNot { point ->
            val mounted = live.filter { it.first == point }
            if (mounted.isEmpty()) return@filterNot false
            // The newest mount on a point wins, so its options are the ones in effect.
            val needsReadOnly = cfg.bindSharedStorage && cfg.sharedStorageReadOnly && point == sharedPoint
            !needsReadOnly || "ro" in mounted.last().second
        }
    }

    /** Rejects shared storage paths that would escape the chroot or confuse /proc/mounts matching. */
    fun validate(cfg: MountConfig): List<String> {
        if (!cfg.bindSharedStorage) return emptyList()
        val errors = mutableListOf<String>()
        val source = cfg.sharedStorageSource
        if (!source.startsWith("/") || source.any { it == '\n' || it == '\u0000' } ||
            source.split('/').any { it == ".." }
        ) {
            errors += "invalid shared storage source: ${cfg.sharedStorageSource}"
        }
        val target = cfg.sharedStorageTarget
        if (!isSafeAbsolutePath(target) || target == "/" || target.endsWith("/") || "//" in target ||
            RESERVED_TARGETS.any { target == it || target.startsWith("$it/") }
        ) {
            errors += "invalid shared storage target: $target"
        }
        return errors
    }

    fun isSafeAbsolutePath(path: String): Boolean =
        SAFE_PATH.matches(path) && path.split('/').none { it == ".." || it == "." }

    /** Unmounts what is actually mounted, deepest first. */
    fun unmountCommands(procMounts: List<String>, chrootDir: String): List<String> =
        residualMounts(procMounts, chrootDir)
            .withIndex()
            // Stacked mounts on the same point come off newest first.
            .sortedWith(
                compareByDescending<IndexedValue<String>> { it.value.count { ch -> ch == '/' } }
                    .thenByDescending { it.index }
            )
            .map { (_, point) ->
                "umount ${quote(point)} 2>/dev/null || umount -l ${quote(point)} 2>/dev/null || true"
            }

    /** Mounts below the chroot that the new config no longer wants, e.g. an old shared target. */
    fun staleMountLines(procMounts: List<String>, cfg: MountConfig, chrootDir: String): List<String> {
        val wanted = expectedMountPoints(cfg, chrootDir).toSet()
        return procMounts.filter { line ->
            val point = residualMounts(listOf(line), chrootDir).singleOrNull() ?: return@filter false
            point !in wanted
        }
    }

    /** A non-empty result means deleting the rootfs is unsafe. */
    fun residualMounts(procMounts: List<String>, chrootDir: String): List<String> {
        val root = chrootDir.trimEnd('/')
        return procMounts.mapNotNull { line ->
            val point = line.split(' ').getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val decoded = decodeOctal(point)
            if (decoded == root || decoded.startsWith("$root/")) decoded else null
        }
    }

    private val SAFE_PATH = Regex("/[A-Za-z0-9._/+-]*")
    private val RESERVED_TARGETS = listOf("/proc", "/sys", "/dev", "/etc", "/usr", "/bin", "/lib")

    private fun decodeOctal(value: String): String =
        Regex("""\\(\d{3})""").replace(value) { m -> m.groupValues[1].toInt(8).toChar().toString() }
}
