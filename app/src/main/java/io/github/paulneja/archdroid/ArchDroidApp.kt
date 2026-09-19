package io.github.paulneja.archdroid

import android.app.Application
import com.topjohnwu.superuser.Shell
import io.github.paulneja.archdroid.core.notify.ArchDroidNotifications

class ArchDroidApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(20)
        )
        ArchDroidNotifications.createChannels(this)
    }
}
