package io.github.paulneja.archdroid.di

import android.annotation.SuppressLint
import android.content.Context
import io.github.paulneja.archdroid.core.chroot.ChrootController
import io.github.paulneja.archdroid.core.chroot.ChrootInstaller
import io.github.paulneja.archdroid.core.service.InstallCoordinator
import io.github.paulneja.archdroid.data.SettingsRepository
import io.github.paulneja.archdroid.data.model.UserConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Both singletons are built from the application context, which outlives the process-wide
 * reference held here, so the static field lint check does not apply.
 */
@SuppressLint("StaticFieldLeak")
object ServiceLocator {

    @Volatile
    private var settings: SettingsRepository? = null

    @Volatile
    private var controller: ChrootController? = null

    @Volatile
    private var coordinator: InstallCoordinator? = null

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
                settings.updateMirror(config.mirror)
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
