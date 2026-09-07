package io.github.paulneja.archdroid.core.chroot

import android.content.Context
import io.github.paulneja.archdroid.core.root.RootManager
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

    suspend fun prepareSession(): MountManager.MountResult {
        val cfg = settings.current()
        writeEnterScript(cfg)
        applyEnv(cfg.env)
        val result = MountManager.ensureMounted(cfg.mounts)
        refresh()
        return result
    }

    suspend fun stop() {
        val cfg = settings.current()
        if (cfg.behavior.shutdownScript.isNotBlank()) {
            runInsideChroot("test -f ${cfg.behavior.shutdownScript} && sh ${cfg.behavior.shutdownScript}")
        }
        MountManager.unmountAll(cfg.mounts)
        refresh()
    }

    suspend fun applyEnv(env: EnvConfig) {
        RootManager.execScript(
            """
            printf 'nameserver %s\nnameserver %s\n' '${env.dnsPrimary}' '${env.dnsSecondary}' > ${ChrootPaths.RESOLV_CONF}
            echo '${env.hostname}' > ${ChrootPaths.CHROOT_DIR}/etc/hostname
            """.trimIndent()
        )
    }

    suspend fun runInsideChroot(command: String) {
        RootManager.execScript("chroot ${ChrootPaths.CHROOT_DIR} /bin/bash -c '$command'")
    }

    fun buildEnterScript(cfg: AppSettings): String = buildString {
        appendLine("#!/system/bin/sh")
        appendLine("export TERM=xterm-256color")
        appendLine("export COLORTERM=truecolor")
        appendLine("export LANG=${cfg.env.lang}")
        cfg.env.extraEnv.forEach { (k, v) ->
            appendLine("export $k='$v'")
        }
        if (cfg.behavior.startupScript.isNotBlank()) {
            appendLine("test -f ${cfg.behavior.startupScript} && sh ${cfg.behavior.startupScript} 2>/dev/null")
        }
        if (cfg.user.loginAsRoot) {
            appendLine("exec chroot ${ChrootPaths.CHROOT_DIR} /bin/bash -l")
        } else {
            appendLine("exec chroot ${ChrootPaths.CHROOT_DIR} /bin/su - ${cfg.user.username}")
        }
    }

    private suspend fun writeEnterScript(cfg: AppSettings) = withContext(Dispatchers.IO) {
        val tmp = File(context.filesDir, "enter.sh")
        tmp.writeText(buildEnterScript(cfg))
        RootManager.exec(
            "cp '${tmp.absolutePath}' '${ChrootPaths.ENTER_SCRIPT}'",
            "chmod 755 '${ChrootPaths.ENTER_SCRIPT}'",
            "chown root:root '${ChrootPaths.ENTER_SCRIPT}'"
        )
    }

    suspend fun exportBackup(destInAppFiles: File): Result<File> = runCatching {
        RootManager.execScript(
            "tar -C '${ChrootPaths.CHROOT_DIR}' -czpf '${destInAppFiles.absolutePath}' ."
        ).also {
            if (!it.isSuccess) error("tar failed: ${it.err.joinToString()}")
        }
        destInAppFiles
    }

    suspend fun importBackup(archiveInAppFiles: File): Result<Unit> = runCatching {
        val cfg = settings.current()
        MountManager.unmountAll(cfg.mounts)
        val script = buildString {
            appendLine("C=${ChrootPaths.CHROOT_DIR}")
            appendLine("mkdir -p \"\$C\"")
            appendLine("rm -rf \"\$C\"/* \"\$C\"/.[!.]* 2>/dev/null")
            appendLine("tar -xpf '${archiveInAppFiles.absolutePath}' -C \"\$C\" 2>/dev/null || tar -xf '${archiveInAppFiles.absolutePath}' -C \"\$C\"")
            appendLine("test -f ${ChrootPaths.MARKER_FILE}")
        }
        RootManager.execScript(script).also {
            if (!it.isSuccess) error("restore failed: ${it.err.joinToString()}")
        }
        refresh()
    }

    suspend fun reset(): Result<Unit> = runCatching {
        val cfg = settings.current()
        MountManager.unmountAll(cfg.mounts)
        RootManager.exec("rm -rf '${ChrootPaths.CHROOT_DIR}'").also {
            if (!it.isSuccess) error("rm failed: ${it.err.joinToString()}")
        }
        refresh()
    }
}
