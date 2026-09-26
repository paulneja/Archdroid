package io.github.paulneja.archdroid.core.desktop

import android.content.Context
import android.content.Intent
import io.github.paulneja.archdroid.core.chroot.ChrootController
import io.github.paulneja.archdroid.core.chroot.ChrootPaths
import io.github.paulneja.archdroid.core.root.RootManager
import io.github.paulneja.archdroid.core.util.ShellQuote.quote
import io.github.paulneja.archdroid.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Installs desktops and runs one graphical session at a time. */
class DesktopController(
    private val context: Context,
    private val chroot: ChrootController,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    /** True while installing or running; keeps the session service in the foreground. */
    private val active: MutableStateFlow<Boolean>,
) {
    data class State(
        val installed: Set<DesktopEnvironment> = emptySet(),
        val installing: Boolean = false,
        val running: Boolean = false,
        val runningEngine: DisplayEngine? = null,
        val starting: Boolean = false,
        val log: List<String> = emptyList(),
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val audio = AudioBridge(scope)
    private val c = ChrootPaths.CHROOT_DIR

    suspend fun refresh() {
        val installed = RootManager.outputOfScript(DesktopScripts.detectInstalledScript(c))
            .mapNotNull { DesktopEnvironment.fromId(it.trim()) }.toSet()
        val engine = DesktopScripts.parseSession(
            RootManager.outputOfScript(DesktopScripts.detectSessionScript(c))
        )
        _state.update {
            it.copy(
                installed = installed,
                running = engine != null,
                runningEngine = engine,
            )
        }
        if (engine != null) audio.start() else audio.stop()
        syncActive()
    }

    fun install(desktops: Set<DesktopEnvironment>, withWayland: Boolean) {
        if (_state.value.installing || desktops.isEmpty()) return
        _state.update { it.copy(installing = true, log = emptyList(), error = null) }
        syncActive()
        scope.launch {
            try {
                val prepared = chroot.prepareSession()
                if (!prepared.ok) error(prepared.log.joinToString("\n"))
                val packages = DesktopScripts.installPackages(desktops, withWayland)
                appendLog("pacman -S ${packages.joinToString(" ")}")
                val result = RootManager.execStreamingIsolated(
                    "chroot $c /bin/bash -lc ${quote("pacman -Sy --noconfirm && " + DesktopScripts.installCommand(packages))} 2>&1",
                    ::appendLog,
                )
                if (!result.isSuccess) error("pacman failed (exit ${result.code})")
                configureUser()
                appendLog("OK")
            } catch (failure: Exception) {
                _state.update { it.copy(error = failure.message ?: failure.toString()) }
            } finally {
                _state.update { it.copy(installing = false) }
                refresh()
            }
        }
    }

    /** Starts the display server and the session, then shows it. */
    suspend fun start(desktop: DesktopEnvironment, session: SessionType, engine: DisplayEngine, width: Int, height: Int) {
        if (_state.value.starting) return
        _state.update { it.copy(starting = true, error = null) }
        try {
            refresh()
            if (_state.value.running) {
                show()
                return
            }
            val prepared = chroot.prepareSession()
            if (!prepared.ok) error(prepared.log.joinToString("\n"))
            writeStartScript()
            configureUser()
            val user = sessionUser()
            val display = when (engine) {
                DisplayEngine.X11_NATIVE -> {
                    if (!RootManager.test(DesktopScripts.x11ServerRunning())) {
                        RootManager.execScript(DesktopScripts.startX11Server(c, context.packageCodePath))
                        waitFor { RootManager.test(DesktopScripts.displayReady(c, ":0")) }
                            || error("the X server did not start")
                    }
                    ":0"
                }
                DisplayEngine.VNC -> {
                    RootManager.execScript(DesktopScripts.startVncServer(c, user, width, height))
                    waitFor { RootManager.test(DesktopScripts.displayReady(c, DesktopScripts.VNC_DISPLAY)) }
                        || error("the VNC server did not start")
                    DesktopScripts.VNC_DISPLAY
                }
            }
            RootManager.execScript(DesktopScripts.startSession(c, user, desktop, session, display))
            audio.start()
            _state.update { it.copy(running = true, runningEngine = engine) }
            syncActive()
            show(engine)
        } catch (failure: Exception) {
            _state.update { it.copy(error = failure.message ?: failure.toString()) }
        } finally {
            _state.update { it.copy(starting = false) }
        }
    }

    fun show(engine: DisplayEngine = _state.value.runningEngine ?: DisplayEngine.X11_NATIVE) {
        val target = when (engine) {
            DisplayEngine.X11_NATIVE -> Intent().setClassName(context, "com.termux.x11.MainActivity")
            DisplayEngine.VNC -> Intent().setClassName(context, "io.github.paulneja.archdroid.ui.desktop.VncActivity")
        }
        context.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    suspend fun stop() {
        val user = sessionUser()
        RootManager.execScript(DesktopScripts.stopSession(c, user))
        delay(STOP_GRACE_MILLIS)
        RootManager.exec(DesktopScripts.stopX11Server(), DesktopScripts.stopVncServer(c))
        audio.stop()
        _state.update { it.copy(running = false, runningEngine = null) }
        syncActive()
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private suspend fun sessionUser(): String =
        settings.current().user.let { if (it.loginAsRoot) "root" else it.username }

    private suspend fun writeStartScript() {
        val file = File(context.filesDir, "desktop-start")
        withContext(Dispatchers.IO) { file.writeText(DesktopScripts.startScript()) }
        val target = quote("$c${DesktopScripts.START_SCRIPT}")
        RootManager.exec(
            "mkdir -p ${quote("$c/usr/lib/archdroid")}",
            "cp ${quote(file.absolutePath)} $target",
            "chmod 755 $target",
        )
    }

    private suspend fun configureUser() {
        RootManager.execScript(
            "chroot $c /bin/bash -lc ${quote(DesktopScripts.configureUserCommand(sessionUser()))}"
        )
    }

    private fun appendLog(line: String) {
        _state.update { it.copy(log = (it.log + line).takeLast(MAX_LOG)) }
    }

    private fun syncActive() {
        active.value = _state.value.installing || _state.value.running
    }

    private suspend fun waitFor(condition: suspend () -> Boolean): Boolean {
        repeat(WAIT_STEPS) {
            if (condition()) return true
            delay(WAIT_STEP_MILLIS)
        }
        return false
    }

    private companion object {
        const val MAX_LOG = 300
        const val WAIT_STEPS = 40
        const val WAIT_STEP_MILLIS = 250L
        const val STOP_GRACE_MILLIS = 1_500L
    }
}
