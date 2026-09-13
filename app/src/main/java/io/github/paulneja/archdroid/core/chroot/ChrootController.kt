package io.github.paulneja.archdroid.core.chroot

import android.content.Context
import io.github.paulneja.archdroid.core.root.RootManager
import io.github.paulneja.archdroid.core.util.RootTar
import io.github.paulneja.archdroid.core.util.ShellQuote.quote
import io.github.paulneja.archdroid.data.SettingsRepository
import io.github.paulneja.archdroid.data.model.AppSettings
import io.github.paulneja.archdroid.data.model.EnvConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class ChrootController(
    private val context: Context,
    private val settings: SettingsRepository,
) {

    class MountsBusyException(val points: List<String>) : Exception(
        "chroot still has active mounts: " + points.joinToString(", ")
    )

    /** True when the mount was asked for directly, not brought up for a session. */
    @Volatile
    private var pinnedByUser = false

    private val _status = MutableStateFlow(ChrootStatus.UNKNOWN)
    val status: StateFlow<ChrootStatus> = _status.asStateFlow()

    suspend fun refresh() {
        _status.value = when {
            !isInstalled() -> ChrootStatus.NOT_INSTALLED
            MountManager.isMounted() -> ChrootStatus.MOUNTED
            else -> ChrootStatus.INSTALLED
        }
    }

    suspend fun isInstalled(): Boolean =
        RootManager.test("test -f ${ChrootPaths.MARKER_FILE}")

    /**
     * Paths a login shell needs that are not there. Empty means the rootfs can be entered.
     */
    suspend fun missingEssentials(): List<String> {
        val cfg = settings.current()
        val required = ChrootHealth.requiredPaths(ChrootPaths.CHROOT_DIR, cfg.user.loginAsRoot)
        return ChrootHealth.missingFrom(
            RootManager.outputOfScript(ChrootHealth.probeScript(required))
        )
    }

    suspend fun prepareSession(pin: Boolean = false): MountManager.MountResult {
        if (pin) pinnedByUser = true
        val cfg = settings.current()
        writeEnterScript(cfg)
        applyEnv(cfg.env)
        val result = MountManager.ensureMounted(cfg.mounts)
        refresh()
        return result
    }

    suspend fun stop(): MountManager.MountResult {
        pinnedByUser = false
        val cfg = settings.current()
        if (cfg.behavior.shutdownScript.isNotBlank()) {
            val script = cfg.behavior.shutdownScript.trim()
            runInsideChroot("test -f ${quote(script)} && sh ${quote(script)}")
        }
        val result = MountManager.unmountAll(cfg.mounts)
        refresh()
        return result
    }

    /**
     * Called when a terminal session ends. Brings the mounts down once nothing is using
     * them, so the chroot directory stops being a path into the real filesystem.
     */
    suspend fun releaseSession(remainingSessions: Int): MountManager.MountResult? {
        if (!MountLifetime.shouldUnmount(pinnedByUser, remainingSessions)) return null
        return stop()
    }

    suspend fun applyEnv(env: EnvConfig) {
        RootManager.execScript(
            buildString {
                appendLine(
                    "printf 'nameserver %s\\nnameserver %s\\n' ${quote(env.dnsPrimary)} " +
                        "${quote(env.dnsSecondary)} > ${ChrootPaths.RESOLV_CONF}"
                )
                appendLine("echo ${quote(env.hostname)} > ${ChrootPaths.CHROOT_DIR}/etc/hostname")
            }
        )
    }

    suspend fun runInsideChroot(command: String) {
        RootManager.execScript("chroot ${ChrootPaths.CHROOT_DIR} /bin/bash -lc ${quote(command)}")
    }

    fun buildEnterScript(cfg: AppSettings): String = EnterScript.build(cfg)

    private suspend fun writeEnterScript(cfg: AppSettings) = withContext(Dispatchers.IO) {
        val tmp = File(context.filesDir, "enter.sh")
        tmp.writeText(EnterScript.build(cfg))
        RootManager.exec(
            "cp ${quote(tmp.absolutePath)} ${quote(ChrootPaths.ENTER_SCRIPT)}",
            "chmod 755 ${quote(ChrootPaths.ENTER_SCRIPT)}",
            "chown root:root ${quote(ChrootPaths.ENTER_SCRIPT)}",
        )
    }

    suspend fun exportBackup(destInAppFiles: File): Result<File> = runCatching {
        val script = buildString {
            append(RootTar.RESOLVE)
            appendLine(
                "\$TAR -C ${quote(ChrootPaths.CHROOT_DIR)} -czpf ${quote(destInAppFiles.absolutePath)} ."
            )
        }
        RootManager.execScript(script).also {
            if (!it.isSuccess) error("tar failed: ${it.err.joinToString()}")
        }
        destInAppFiles
    }

    suspend fun importBackup(archiveInAppFiles: File): Result<Unit> = runCatching {
        clearRootfs()
        val script = buildString {
            append(RootTar.RESOLVE)
            appendLine("C=${quote(ChrootPaths.CHROOT_DIR)}")
            appendLine("\$TAR -xpf ${quote(archiveInAppFiles.absolutePath)} -C \"\$C\"")
            appendLine("test -f ${ChrootPaths.MARKER_FILE}")
        }
        RootManager.execScript(script).also {
            if (!it.isSuccess) error("restore failed: ${it.err.joinToString()}")
        }
        refresh()
    }

    suspend fun reset(): Result<Unit> = runCatching {
        ensureUnmounted()
        RootManager.exec("rm -rf ${quote(ChrootPaths.CHROOT_DIR)}").also {
            if (!it.isSuccess) error("rm failed: ${it.err.joinToString()}")
        }
        refresh()
    }

    /** Wipes the rootfs contents but keeps the directory, for a restore on top. */
    private suspend fun clearRootfs() {
        ensureUnmounted()
        val c = quote(ChrootPaths.CHROOT_DIR)
        RootManager.execScript(
            buildString {
                appendLine("C=$c")
                appendLine("mkdir -p \"\$C\"")
                appendLine("rm -rf \"\$C\"/* \"\$C\"/.[!.]* 2>/dev/null")
                appendLine("true")
            }
        )
    }

    /**
     * Unmounts and refuses to continue while anything is still mounted under the chroot.
     * Deleting through a live bind mount would destroy the directory it points at — the
     * shared-storage bind makes that the user's real /sdcard.
     */
    private suspend fun ensureUnmounted() {
        val cfg = settings.current()
        MountManager.unmountAll(cfg.mounts)
        val residual = MountManager.residualMounts()
        if (residual.isNotEmpty()) throw MountsBusyException(residual)
    }
}
