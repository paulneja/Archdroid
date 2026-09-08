package io.github.paulneja.archdroid.core.chroot

import io.github.paulneja.archdroid.core.root.RootManager
import io.github.paulneja.archdroid.data.model.MountConfig

object MountManager {

    private const val C = ChrootPaths.CHROOT_DIR

    data class MountResult(val ok: Boolean, val log: List<String>)

    suspend fun ensureMounted(cfg: MountConfig): MountResult {
        val result = RootManager.exec(*MountPlan.mountCommands(cfg, C).toTypedArray())
        return MountResult(result.isSuccess, result.out + result.err)
    }

    /**
     * Unmounts everything this config can mount and then re-reads /proc/mounts: the result
     * is only ok when nothing is left below the chroot, since callers use that to decide
     * whether deleting the rootfs is safe.
     */
    suspend fun unmountAll(cfg: MountConfig): MountResult {
        val result = RootManager.exec(*MountPlan.unmountCommands(cfg, C).toTypedArray())
        val residual = residualMounts()
        return MountResult(
            ok = residual.isEmpty(),
            log = result.out + result.err + residual.map { "still mounted: $it" },
        )
    }

    suspend fun isMounted(): Boolean =
        RootManager.test("grep -q ' $C/proc ' /proc/mounts")

    suspend fun activeMounts(): List<String> =
        MountPlan.residualMounts(RootManager.outputOf("cat /proc/mounts"), C)

    /** Mount points still live at or below the chroot — must be empty before an rm -rf. */
    suspend fun residualMounts(): List<String> = activeMounts()
}
