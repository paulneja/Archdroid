package io.github.paulneja.archdroid.di

import android.content.Context
import io.github.paulneja.archdroid.core.chroot.ChrootController
import io.github.paulneja.archdroid.data.SettingsRepository

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
