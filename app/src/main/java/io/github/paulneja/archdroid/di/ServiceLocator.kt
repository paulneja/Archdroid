package io.github.paulneja.archdroid.di

import android.annotation.SuppressLint
import android.content.Context
import io.github.paulneja.archdroid.core.chroot.ChrootController
import io.github.paulneja.archdroid.core.chroot.ChrootInstaller
import io.github.paulneja.archdroid.core.desktop.DesktopController
import io.github.paulneja.archdroid.core.protect.AppProtection
import io.github.paulneja.archdroid.core.service.InstallCoordinator
import io.github.paulneja.archdroid.core.service.SessionService
import io.github.paulneja.archdroid.core.terminal.SessionStore
import io.github.paulneja.archdroid.core.terminal.SessionSurvival
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import io.github.paulneja.archdroid.data.SettingsRepository
import io.github.paulneja.archdroid.data.model.UserConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@SuppressLint("StaticFieldLeak")
object ServiceLocator {

    @Volatile
    private var settings: SettingsRepository? = null

    @Volatile
    private var controller: ChrootController? = null

    @Volatile
    private var coordinator: InstallCoordinator? = null

    @Volatile
    private var sessions: SessionStore? = null

    /** Set by the desktop while a graphical session runs; it keeps the session service up. */
    val desktopRunning = MutableStateFlow(false)

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var desktop: DesktopController? = null

    fun desktop(context: Context): DesktopController =
        desktop ?: synchronized(this) {
            desktop ?: DesktopController(
                context.applicationContext, chroot(context), settings(context), mainScope, desktopRunning,
            ).also { desktop = it }
        }

    @Volatile
    private var protection: AppProtection? = null

    fun protection(context: Context): AppProtection =
        protection ?: synchronized(this) {
            protection ?: AppProtection(context.applicationContext, settings(context), mainScope)
                .also { protection = it }
        }

    fun sessionStore(context: Context): SessionStore =
        sessions ?: synchronized(this) {
            sessions ?: SessionStore(context.applicationContext, chroot(context), mainScope)
                .also {
                    sessions = it
                    watchSurvival(context.applicationContext, it)
                }
        }

    /** Starts the session service whenever something needs to outlive the UI. */
    private fun watchSurvival(context: Context, store: SessionStore) {
        mainScope.launch {
            combine(
                store.tabs,
                store.protectionChanges,
                settings(context).settings,
                desktopRunning,
            ) { tabs, _, appSettings, desktop ->
                SessionSurvival.shouldRun(
                    survivalEnabled = appSettings.behavior.terminalSurvival,
                    sessions = tabs.size,
                    protectedSessions = tabs.count { it.protected.value },
                    desktopRunning = desktop,
                )
            }.distinctUntilChanged().collect { run ->
                if (run) SessionService.ensureRunning(context)
            }
        }
    }

    fun settings(context: Context): SettingsRepository =
        settings ?: synchronized(this) {
            settings ?: SettingsRepository(context.applicationContext).also { settings = it }
        }

    fun chroot(context: Context): ChrootController =
        controller ?: synchronized(this) {
            controller ?: ChrootController(context.applicationContext, settings(context))
                .also { controller = it }
        }

    fun installCoordinator(context: Context): InstallCoordinator =
        coordinator ?: synchronized(this) {
            coordinator ?: createInstallCoordinator(context.applicationContext)
                .also { coordinator = it }
        }

    private fun createInstallCoordinator(context: Context): InstallCoordinator {
        val installer = ChrootInstaller(context)
        val settings = settings(context)
        val chroot = chroot(context)
        return InstallCoordinator(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            installer = { config, onProgress, onLog ->
                installer.install(config, onProgress, onLog)
            },
            onSuccess = { config ->
                settings.updateDownloadMode(config.downloadMode)
                settings.updateUser(
                    UserConfig(
                        username = config.username.ifBlank { "root" },
                        loginAsRoot = config.username.isBlank(),
                    )
                )
                chroot.refresh()
            },
        )
    }
}
