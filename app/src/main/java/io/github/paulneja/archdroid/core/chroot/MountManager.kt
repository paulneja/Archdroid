package io.github.paulneja.archdroid.core.chroot

import io.github.paulneja.archdroid.core.root.RootManager
import io.github.paulneja.archdroid.data.model.MountConfig

object MountManager {

    private const val C = ChrootPaths.CHROOT_DIR

    private const val MAX_UNMOUNT_PASSES = 8

    data class MountResult(val ok: Boolean, val log: List<String>)

    suspend fun ensureMounted(cfg: MountConfig): MountResult {
        val invalid = MountPlan.validate(cfg)
        if (invalid.isNotEmpty()) return MountResult(ok = false, log = invalid)
        val result = RootManager.exec(*MountPlan.mountCommands(cfg, C).toTypedArray())
        val missing = MountPlan.missingMounts(RootManager.outputOf("cat /proc/mounts"), cfg, C)
        return MountResult(
            ok = missing.isEmpty(),
            log = result.out + result.err + missing.map { "not mounted: $it" },
        )
    }

    suspend fun reconcile(cfg: MountConfig): MountResult {
        val invalid = MountPlan.validate(cfg)
        if (invalid.isNotEmpty()) return MountResult(ok = false, log = invalid)
        val stale = MountPlan.staleMountLines(RootManager.outputOf("cat /proc/mounts"), cfg, C)
        val dropped = RootManager.exec(*MountPlan.unmountCommands(stale, C).toTypedArray())
        val added = ensureMounted(cfg)
        return added.copy(log = dropped.out + dropped.err + added.log)
    }

    suspend fun unmountAll(): MountResult {
        val log = mutableListOf<String>()
        var residual = residualMounts()
        var passes = 0
        while (residual.isNotEmpty() && passes < MAX_UNMOUNT_PASSES) {
            val procMounts = RootManager.outputOf("cat /proc/mounts")
            val result = RootManager.exec(*MountPlan.unmountCommands(procMounts, C).toTypedArray())
            log += result.out + result.err
            val next = residualMounts()
            if (next.size >= residual.size) {
                residual = next
                break
            }
            residual = next
            passes++
        }
        return MountResult(ok = residual.isEmpty(), log = log + residual.map { "still mounted: $it" })
    }

    suspend fun isMounted(): Boolean =
        RootManager.test("grep -q ' $C/proc ' /proc/mounts")

    suspend fun activeMounts(): List<String> =
        MountPlan.residualMounts(RootManager.outputOf("cat /proc/mounts"), C).distinct()

    suspend fun residualMounts(): List<String> =
        MountPlan.residualMounts(RootManager.outputOf("cat /proc/mounts"), C)
}
