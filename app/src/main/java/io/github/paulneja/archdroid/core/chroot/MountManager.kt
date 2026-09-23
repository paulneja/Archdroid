package io.github.paulneja.archdroid.core.chroot

import io.github.paulneja.archdroid.core.root.RootManager
import io.github.paulneja.archdroid.data.model.MountConfig

object MountManager {

    private const val C = ChrootPaths.CHROOT_DIR

    data class MountResult(val ok: Boolean, val log: List<String>)

    /** Verifies the result through /proc/mounts instead of the last shell exit code. */
    suspend fun ensureMounted(cfg: MountConfig): MountResult {
        val result = RootManager.exec(*MountPlan.mountCommands(cfg, C).toTypedArray())
        val missing = MountPlan.missingMounts(RootManager.outputOf("cat /proc/mounts"), cfg, C)
        return MountResult(
            ok = missing.isEmpty(),
            log = result.out + result.err + missing.map { "not mounted: $it" },
        )
    }

    /** Reports failure while any mount remains below the chroot. */
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

    /** Must be empty before deleting the rootfs. */
    suspend fun residualMounts(): List<String> = activeMounts()
}
