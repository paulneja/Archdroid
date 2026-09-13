package io.github.paulneja.archdroid.core.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.paulneja.archdroid.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = ServiceLocator.settings(context)
                if (settings.current().behavior.autostartOnBoot) {
                    ServiceLocator.chroot(context).prepareSession(pin = true)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
