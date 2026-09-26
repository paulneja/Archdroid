package io.github.paulneja.archdroid.core.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.paulneja.archdroid.MainActivity
import io.github.paulneja.archdroid.R
import io.github.paulneja.archdroid.core.notify.ArchDroidNotifications
import io.github.paulneja.archdroid.core.terminal.SessionSurvival
import io.github.paulneja.archdroid.di.ServiceLocator
import io.github.paulneja.archdroid.ui.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private val wakeLockHeld = MutableStateFlow(false)
    private var inForeground = false

    override fun onCreate() {
        super.onCreate()
        val store = ServiceLocator.sessionStore(this)
        val settings = ServiceLocator.settings(this)
        val desktopRunning = ServiceLocator.desktopRunning
        scope.launch {
            combine(
                store.tabs,
                store.protectionChanges,
                settings.settings,
                desktopRunning,
                wakeLockHeld,
            ) { tabs, _, appSettings, desktop, wake ->
                val protectedCount = tabs.count { it.protected.value }
                State(
                    run = SessionSurvival.shouldRun(
                        survivalEnabled = appSettings.behavior.terminalSurvival,
                        sessions = tabs.size,
                        protectedSessions = protectedCount,
                        desktopRunning = desktop,
                    ),
                    sessions = tabs.size,
                    protectedSessions = protectedCount,
                    desktop = desktop,
                    wakeLock = wake,
                )
            }.collect { state ->
                if (state.run) {
                    show(state)
                } else {
                    stopSelf()
                }
            }
        }
    }

    private data class State(
        val run: Boolean,
        val sessions: Int,
        val protectedSessions: Int,
        val desktop: Boolean,
        val wakeLock: Boolean,
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Required promptly after startForegroundService.
        if (!inForeground) show(State(true, 0, 0, false, wakeLockHeld.value))
        when (intent?.action) {
            ACTION_EXIT -> ServiceLocator.sessionStore(this).closeUnprotected()
            ACTION_TOGGLE_WAKELOCK -> toggleWakeLock()
        }
        return START_NOT_STICKY
    }

    private fun show(state: State) {
        ServiceCompat.startForeground(
            this,
            ArchDroidNotifications.SESSION_NOTIFICATION_ID,
            build(state),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        inForeground = true
    }

    private fun build(state: State): Notification {
        val parts = mutableListOf(
            resources.getQuantityString(R.plurals.session_count, state.sessions, state.sessions)
        )
        if (state.protectedSessions > 0) {
            parts += resources.getQuantityString(
                R.plurals.session_protected_count, state.protectedSessions, state.protectedSessions,
            )
        }
        if (state.desktop) parts += getString(R.string.session_desktop_running)
        if (state.wakeLock) parts += getString(R.string.session_wakelock_held)

        return NotificationCompat.Builder(this, ArchDroidNotifications.CHANNEL_SESSIONS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.session_notification_title))
            .setContentText(parts.joinToString(" · "))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openTerminalIntent())
            .addAction(0, getString(R.string.session_action_exit), actionIntent(ACTION_EXIT, 31))
            .addAction(
                0,
                getString(
                    if (state.wakeLock) R.string.session_action_release_wakelock
                    else R.string.session_action_acquire_wakelock
                ),
                actionIntent(ACTION_TOGGLE_WAKELOCK, 32),
            )
            .build()
    }

    @SuppressLint("WakelockTimeout") // Held on purpose until the user releases it, like Termux.
    private fun toggleWakeLock() {
        val held = wakeLock
        if (held != null) {
            if (held.isHeld) held.release()
            wakeLock = null
            wakeLockHeld.value = false
        } else {
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "archdroid:sessions").apply {
                setReferenceCounted(false)
                acquire()
            }
            wakeLockHeld.value = true
        }
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, SessionService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openTerminalIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        30,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_ROUTE, Routes.TERMINAL),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_EXIT = "io.github.paulneja.archdroid.action.EXIT_SESSIONS"
        const val ACTION_TOGGLE_WAKELOCK = "io.github.paulneja.archdroid.action.TOGGLE_WAKELOCK"

        fun ensureRunning(context: Context) {
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, SessionService::class.java),
            )
        }
    }
}
