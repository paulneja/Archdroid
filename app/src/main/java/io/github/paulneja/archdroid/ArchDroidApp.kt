package io.github.paulneja.archdroid

import android.app.Application
import com.topjohnwu.superuser.BusyBoxInstaller
import com.topjohnwu.superuser.Shell

class ArchDroidApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setInitializers(BusyBoxInstaller::class.java)
                .setTimeout(20)
        )
    }
}
