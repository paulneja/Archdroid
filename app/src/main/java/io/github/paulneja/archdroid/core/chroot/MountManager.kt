package io.github.paulneja.archdroid.core.chroot

import io.github.paulneja.archdroid.core.root.RootManager
import io.github.paulneja.archdroid.data.model.MountConfig

object MountManager {

    private const val C = ChrootPaths.CHROOT_DIR

    data class MountResult(val ok: Boolean, val log: List<String>)

    suspend fun ensureMounted(cfg: MountConfig): MountResult {
        val cmds = mutableListOf<String>()
        cmds += "mkdir -p $C/proc $C/sys $C/dev $C/dev/pts $C/dev/shm $C/mnt"

        if (cfg.proc) cmds += "grep -q ' $C/proc ' /proc/mounts || mount -t proc proc $C/proc"
        if (cfg.sys) cmds += "grep -q ' $C/sys ' /proc/mounts || mount -t sysfs sysfs $C/sys"
        if (cfg.dev) cmds += "grep -q ' $C/dev ' /proc/mounts || mount --bind /dev $C/dev"
        if (cfg.devPts) cmds += "grep -q ' $C/dev/pts ' /proc/mounts || mount -t devpts devpts $C/dev/pts"
        if (cfg.shm) cmds += "grep -q ' $C/dev/shm ' /proc/mounts || mount -t tmpfs -o size=${cfg.shmSizeMb}M tmpfs $C/dev/shm"

        if (cfg.bindSharedStorage) {
            val dst = "$C${cfg.sharedStorageTarget}"
            cmds += "mkdir -p '$dst'"
            cmds += "grep -q ' $dst ' /proc/mounts || mount --bind '${cfg.sharedStorageSource}' '$dst'"
            if (cfg.sharedStorageReadOnly) {
                cmds += "mount -o remount,ro,bind '$dst' 2>/dev/null || true"
            }
        }

        val result = RootManager.exec(*cmds.toTypedArray())
        return MountResult(result.isSuccess, result.out + result.err)
    }

    suspend fun unmountAll(cfg: MountConfig): MountResult {
        val cmds = mutableListOf<String>()
        if (cfg.bindSharedStorage) cmds += "umount '$C${cfg.sharedStorageTarget}' 2>/dev/null || true"
        cmds += listOf(
            "umount $C/dev/shm 2>/dev/null || true",
            "umount $C/dev/pts 2>/dev/null || true",
            "umount $C/dev 2>/dev/null || true",
            "umount $C/sys 2>/dev/null || true",
            "umount $C/proc 2>/dev/null || true",
        )
        val result = RootManager.exec(*cmds.toTypedArray())
        return MountResult(true, result.out + result.err)
    }

    suspend fun isMounted(): Boolean =
        RootManager.test("grep -q ' $C/proc ' /proc/mounts")

    suspend fun activeMounts(): List<String> =
        RootManager.outputOf("grep ' $C' /proc/mounts")
}
