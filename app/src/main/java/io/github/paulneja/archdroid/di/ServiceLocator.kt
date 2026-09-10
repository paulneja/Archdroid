package io.github.paulneja.archdroid.di

import android.annotation.SuppressLint
import android.content.Context
import io.github.paulneja.archdroid.core.chroot.ChrootController
import io.github.paulneja.archdroid.data.SettingsRepository

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

    fun settings(context: Context): SettingsRepository =
        settings ?: synchronized(this) {
            settings ?: SettingsRepository(context.applicationContext).also { settings = it }
        }

    fun chroot(context: Context): ChrootController =
        controller ?: synchronized(this) {
            controller ?: ChrootController(context.applicationContext, settings(context))
                .also { controller = it }
        }
}
