package io.github.paulneja.archdroid

import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.termux.x11.CmdEntryPoint
import com.termux.x11.LorieApp
import android.content.Context
import io.github.paulneja.archdroid.core.util.AppLanguage
import io.github.paulneja.archdroid.di.ServiceLocator
import com.topjohnwu.superuser.Shell
import io.github.paulneja.archdroid.core.notify.ArchDroidNotifications

class ArchDroidApp : LorieApp() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(20)
        )
        ArchDroidNotifications.createChannels(this)
        registerX11Receiver()

        ServiceLocator.protection(this).startWatchdog()
    }

    private fun registerX11Receiver() {
        val filter = IntentFilter().apply {
            X11_ACTIONS.forEach(::addAction)
        }
        ContextCompat.registerReceiver(
            this,
            LorieApp.Receiver(),
            filter,
            "$packageName.permission.X11",
            null,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private companion object {
        val X11_ACTIONS = listOf(
            CmdEntryPoint.ACTION_START,
            "com.termux.x11.ACTION_STOP",
            "com.termux.x11.ACTION_PREFERENCES_CHANGED",
            "com.termux.x11.ACTION_CUSTOM",
        )
    }
}
