package io.github.paulneja.archdroid.core.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import io.github.paulneja.archdroid.core.notify.ArchDroidNotifications
import io.github.paulneja.archdroid.core.notify.InstallNotificationModel
import io.github.paulneja.archdroid.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ArchDroidService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var coordinator: InstallCoordinator
    private lateinit var notifications: ArchDroidNotifications
    private var receivedCommand = false

    override fun onCreate() {
        super.onCreate()
        coordinator = ServiceLocator.installCoordinator(this)
        notifications = ArchDroidNotifications(this)
        serviceScope.launch {
            coordinator.state.collectLatest { state ->
                if (receivedCommand) render(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        receivedCommand = true
        when (intent?.action) {
            ACTION_START -> {
                enterForeground()
                coordinator.startQueued()
            }
            ACTION_RESUME -> {
                enterForeground()
                coordinator.resume()
            }
            ACTION_RETRY -> {
                enterForeground()
                coordinator.retry()
            }
            ACTION_PAUSE -> coordinator.pause()
            ACTION_CANCEL -> coordinator.cancel()
            else -> Unit
        }
        render(coordinator.state.value)
        return START_NOT_STICKY
    }

    private fun enterForeground() {
        val state = coordinator.state.value
        val model = when (state) {
            InstallState.Idle,
            InstallState.Succeeded,
            InstallState.Cancelled,
            is InstallState.Failed,
            -> InstallNotificationModel.from(InstallState.Queued)
            else -> InstallNotificationModel.from(state)
        }
        ServiceCompat.startForeground(
            this,
            ArchDroidNotifications.INSTALL_NOTIFICATION_ID,
            notifications.build(model),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun render(state: InstallState) {
        val model = InstallNotificationModel.from(state)
        when (state) {
            InstallState.Idle -> stopServiceForeground()
            InstallState.Queued,
            is InstallState.Running,
            is InstallState.Paused,
            -> ServiceCompat.startForeground(
                this,
                ArchDroidNotifications.INSTALL_NOTIFICATION_ID,
                notifications.build(model),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
            InstallState.Succeeded -> {
                stopServiceForeground()
                notifications.postEvent(model)
            }
            is InstallState.Failed -> {
                stopServiceForeground()
                notifications.postEvent(model)
            }
            InstallState.Cancelled -> {
                stopServiceForeground()
                notifications.cancelEvent()
            }
        }
    }

    private fun stopServiceForeground() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        notifications.cancelInstall()
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "io.github.paulneja.archdroid.action.START_INSTALL"
        const val ACTION_PAUSE = "io.github.paulneja.archdroid.action.PAUSE_INSTALL"
        const val ACTION_RESUME = "io.github.paulneja.archdroid.action.RESUME_INSTALL"
        const val ACTION_CANCEL = "io.github.paulneja.archdroid.action.CANCEL_INSTALL"
        const val ACTION_RETRY = "io.github.paulneja.archdroid.action.RETRY_INSTALL"
    }
}
