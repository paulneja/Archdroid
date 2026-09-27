package io.github.paulneja.archdroid.core.chroot

import android.content.Context
import io.github.paulneja.archdroid.core.init.CompatInit
import io.github.paulneja.archdroid.core.init.InitSystem
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

    suspend fun missingEssentials(): List<String> {
        val cfg = settings.current()
        val required = ChrootHealth.requiredPaths(ChrootPaths.CHROOT_DIR, cfg.user.loginAsRoot)
        var script = ChrootHealth.probeScript(required)
        if (!cfg.user.loginAsRoot) {
            script += "\n" + ChrootHealth.userProbe(ChrootPaths.CHROOT_DIR, cfg.user.username)
        }
        return ChrootHealth.missingFrom(RootManager.outputOfScript(script))
    }

    /** Until a user is chosen, log in with the account that exists in the rootfs. */
    private suspend fun adoptExistingUser() {
        if (settings.hasChosenUser()) return
        val passwd = RootManager.outputOf("cat ${quote("${ChrootPaths.CHROOT_DIR}/etc/passwd")}")
        val existing = ChrootHealth.existingLoginUser(passwd) ?: return
        val current = settings.current().user
        if (current.loginAsRoot || current.username == existing) return
        settings.updateUser(current.copy(username = existing))
    }

    suspend fun prepareSession(): MountManager.MountResult {
        // Before the enter script is written: it bakes in the login user.
        adoptExistingUser()
        val cfg = settings.current()
        val migrated = RootManager.execScript(UserSetup.migrateLegacyUsersScript(ChrootPaths.CHROOT_DIR))
        if (!migrated.isSuccess) return MountManager.MountResult(
            false,
            (migrated.out + migrated.err).ifEmpty { listOf("User migration failed (exit ${migrated.code})") },
        )
        writeEnterScript(cfg)
        applyEnv(cfg.env)
        val result = MountManager.ensureMounted(cfg.mounts)
        if (result.ok) {
            if (!cfg.user.loginAsRoot) {
                val user = ensureUser(cfg.user.username, null)
                if (user.isFailure) return MountManager.MountResult(false, listOf(user.exceptionOrNull().toString()))
            }
            initSystem.start(cfg.initMode)
        }
        refresh()
        return result
    }

    private val initSystem = InitSystem(context)

    suspend fun stop(): MountManager.MountResult {
        val cfg = settings.current()
        if (cfg.behavior.shutdownScript.isNotBlank()) {
            val script = cfg.behavior.shutdownScript.trim()
            runInsideChroot("test -f ${quote(script)} && sh ${quote(script)}")
        }
        initSystem.stop(cfg.initMode)
        // Nothing may keep running on a rootfs whose mounts are about to disappear.
        RootManager.execScript(CompatInit.killChrootProcessesScript(ChrootPaths.CHROOT_DIR))
        val result = MountManager.unmountAll()
        refresh()
        return result
    }

    suspend fun applyEnv(env: EnvConfig) {
        val cmds = EtcConfig.resolvConfCommands(
            ChrootPaths.CHROOT_DIR, env.dnsPrimary, env.dnsSecondary,
        ) + EtcConfig.hostnameCommands(ChrootPaths.CHROOT_DIR, env.hostname) +
            EtcConfig.localtimeCommands(ChrootPaths.CHROOT_DIR)
        RootManager.exec(*cmds.toTypedArray())
    }

    suspend fun runInsideChroot(command: String) {
        RootManager.execScript("chroot ${ChrootPaths.CHROOT_DIR} /bin/bash -lc ${quote(command)}")
    }

    /** Creates the user when it is missing and optionally sets its password. */
    suspend fun ensureUser(username: String, password: String?): Result<Unit> = runCatching {
        val create = RootManager.execScript(
            "chroot ${ChrootPaths.CHROOT_DIR} /bin/bash -lc ${quote(UserSetup.ensureUserCommand(username))}"
        )
        if (!create.isSuccess) error("useradd: ${create.err.joinToString()}")
        if (password != null) {
            val set = RootManager.execScript(
                UserSetup.setPasswordScript(ChrootPaths.CHROOT_DIR, username, password)
            )
            if (!set.isSuccess) error("chpasswd: ${set.err.joinToString()}")
        }
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

    /** Unmounts first so /proc, /sys, /dev and shared storage never end up in the archive. */
    suspend fun exportBackup(destInAppFiles: File): Result<File> = runCatching {
        ensureUnmounted()
        val script = buildString {
            append(RootTar.RESOLVE)
            appendLine(
                "\$TAR -C ${quote(ChrootPaths.CHROOT_DIR)} -czpf ${quote(destInAppFiles.absolutePath)} . || exit \$?"
            )
            // The app copies the archive out, so it must be able to read what root wrote.
            appendLine("chmod 644 ${quote(destInAppFiles.absolutePath)}")
        }
        RootManager.execScript(script).also {
            if (!it.isSuccess) error("tar failed: ${it.err.joinToString()}")
        }
        destInAppFiles
    }

    /** Checks the whole archive before anything in the current rootfs is deleted. */
    suspend fun importBackup(archiveInAppFiles: File): Result<Unit> = runCatching {
        val listing = RootManager.execScript(BackupCheck.script(archiveInAppFiles.absolutePath))
        BackupCheck.requireRootfs(listing.out)
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
        InstallFiles.all(context.filesDir).forEach(File::delete)
        refresh()
    }

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

    /** Refuses deletion while a bind mount could expose files outside the rootfs. */
    private suspend fun ensureUnmounted() {
        MountManager.unmountAll()
        val residual = MountManager.residualMounts()
        if (residual.isNotEmpty()) throw MountsBusyException(residual)
    }
}
