package io.github.paulneja.archdroid.core.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.paulneja.archdroid.MainActivity
import io.github.paulneja.archdroid.R
import io.github.paulneja.archdroid.core.chroot.ChrootInstaller
import io.github.paulneja.archdroid.core.service.ArchDroidService
import io.github.paulneja.archdroid.ui.Routes

class ArchDroidNotifications(private val context: Context) {

    fun build(model: InstallNotificationModel): Notification {
        val event = !model.ongoing
        val builder = NotificationCompat.Builder(context, if (event) CHANNEL_EVENTS else CHANNEL_INSTALL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title(model))
            .setContentText(body(model))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body(model)))
            .setOnlyAlertOnce(model.ongoing)
            .setOngoing(model.ongoing)
            .setAutoCancel(event)
            .setPriority(if (event) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_LOW)
            .setCategory(
                if (model.ongoing) NotificationCompat.CATEGORY_PROGRESS
                else NotificationCompat.CATEGORY_STATUS
            )

        if (model.ongoing) {
            val percent = model.progressPercent
            builder.setProgress(100, percent ?: 0, percent == null)
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        model.actions.forEach { action ->
            builder.addAction(0, actionLabel(action), pendingIntent(action))
        }
        builder.setContentIntent(
            if (model.kind == InstallNotificationKind.SUCCEEDED) openTerminalIntent()
            else openAppIntent()
        )
        return builder.build()
    }

    fun postEvent(model: InstallNotificationModel) {
        try {
            NotificationManagerCompat.from(context).notify(EVENT_NOTIFICATION_ID, build(model))
        } catch (_: SecurityException) {
            // Notification permission denial must not turn a completed install into failure.
        }
    }

    fun cancelInstall() = NotificationManagerCompat.from(context).cancel(INSTALL_NOTIFICATION_ID)

    fun postInstall(model: InstallNotificationModel) {
        try {
            NotificationManagerCompat.from(context).notify(INSTALL_NOTIFICATION_ID, build(model))
        } catch (_: SecurityException) {
            // A denied runtime permission is already explained in the wizard.
        }
    }

    fun cancelEvent() = NotificationManagerCompat.from(context).cancel(EVENT_NOTIFICATION_ID)

    private fun title(model: InstallNotificationModel): String = when (model.kind) {
        InstallNotificationKind.IDLE,
        InstallNotificationKind.QUEUED,
        InstallNotificationKind.RUNNING,
        -> context.getString(R.string.notification_install_title)
        InstallNotificationKind.STOPPING -> context.getString(R.string.notification_stopping_title)
        InstallNotificationKind.PAUSED -> context.getString(R.string.notification_paused_title)
        InstallNotificationKind.SUCCEEDED -> context.getString(R.string.notification_success_title)
        InstallNotificationKind.FAILED -> context.getString(R.string.notification_failed_title)
        InstallNotificationKind.CANCELLED -> context.getString(R.string.notification_cancelled_title)
    }

    private fun body(model: InstallNotificationModel): String = when (model.kind) {
        InstallNotificationKind.IDLE,
        InstallNotificationKind.QUEUED,
        -> context.getString(R.string.notification_preparing)
        InstallNotificationKind.RUNNING -> runningBody(model)
        InstallNotificationKind.STOPPING -> context.getString(R.string.notification_stopping_body)
        InstallNotificationKind.PAUSED -> pausedBody(model)
        InstallNotificationKind.SUCCEEDED -> context.getString(R.string.notification_success_body)
        InstallNotificationKind.FAILED -> listOfNotNull(
            model.step?.let(::stepName),
            model.message.takeIf { it.isNotBlank() },
        ).joinToString(": ")
        InstallNotificationKind.CANCELLED -> context.getString(R.string.notification_cancelled_body)
    }

    private fun runningBody(model: InstallNotificationModel): String {
        val parts = mutableListOf(model.step?.let(::stepName).orEmpty())
        if (model.step == ChrootInstaller.Step.DOWNLOAD) {
            if (model.totalBytes > 0) {
                parts += context.getString(
                    R.string.notification_progress_bytes,
                    formatBytes(model.downloadedBytes),
                    formatBytes(model.totalBytes),
                )
            } else if (model.downloadedBytes > 0) {
                parts += formatBytes(model.downloadedBytes)
            }
            if (model.bytesPerSecond > 0) {
                parts += context.getString(
                    R.string.notification_rate,
                    formatBytes(model.bytesPerSecond),
                )
            }
            if (model.etaSeconds >= 0) {
                parts += context.getString(R.string.notification_eta, formatDuration(model.etaSeconds))
            }
        } else if (model.detail.isNotBlank()) {
            parts += model.detail
        }
        return parts.filter { it.isNotBlank() }.joinToString(" · ")
    }

    private fun pausedBody(model: InstallNotificationModel): String {
        val progress = if (model.totalBytes > 0) {
            context.getString(
                R.string.notification_progress_bytes,
                formatBytes(model.downloadedBytes),
                formatBytes(model.totalBytes),
            )
        } else {
            stepName(model.step ?: ChrootInstaller.Step.DOWNLOAD)
        }
        return context.getString(R.string.notification_paused_body, progress)
    }

    private fun stepName(step: ChrootInstaller.Step): String = context.getString(
        when (step) {
            ChrootInstaller.Step.DOWNLOAD -> R.string.step_download
            ChrootInstaller.Step.VERIFY -> R.string.step_verify
            ChrootInstaller.Step.EXTRACT -> R.string.step_extract
            ChrootInstaller.Step.BASE_CONFIG -> R.string.step_base_config
            ChrootInstaller.Step.USER -> R.string.step_user
            ChrootInstaller.Step.PACKAGES -> R.string.step_packages
            ChrootInstaller.Step.CLEANUP -> R.string.step_cleanup
        }
    )

    private fun actionLabel(action: InstallNotificationAction): String = context.getString(
        when (action) {
            InstallNotificationAction.PAUSE -> R.string.notification_action_pause
            InstallNotificationAction.RESUME -> R.string.notification_action_resume
            InstallNotificationAction.CANCEL -> R.string.notification_action_cancel
            InstallNotificationAction.RETRY -> R.string.notification_action_retry
            InstallNotificationAction.OPEN_TERMINAL -> R.string.notification_action_open_terminal
        }
    )

    private fun pendingIntent(action: InstallNotificationAction): PendingIntent = when (action) {
        InstallNotificationAction.OPEN_TERMINAL -> openTerminalIntent()
        else -> {
            val (serviceAction, requestCode) = when (action) {
                InstallNotificationAction.PAUSE -> ArchDroidService.ACTION_PAUSE to 11
                InstallNotificationAction.RESUME -> ArchDroidService.ACTION_RESUME to 12
                InstallNotificationAction.CANCEL -> ArchDroidService.ACTION_CANCEL to 13
                InstallNotificationAction.RETRY -> ArchDroidService.ACTION_RETRY to 14
                InstallNotificationAction.OPEN_TERMINAL -> error("handled above")
            }
            val intent = Intent(context, ArchDroidService::class.java).setAction(serviceAction)
            if (action == InstallNotificationAction.RESUME || action == InstallNotificationAction.RETRY) {
                PendingIntent.getForegroundService(
                    context,
                    requestCode,
                    intent,
                    PENDING_INTENT_FLAGS,
                )
            } else {
                PendingIntent.getService(context, requestCode, intent, PENDING_INTENT_FLAGS)
            }
        }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        20,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PENDING_INTENT_FLAGS,
    )

    private fun openTerminalIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        21,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_ROUTE, Routes.TERMINAL),
        PENDING_INTENT_FLAGS,
    )

    private fun formatBytes(bytes: Long): String =
        Formatter.formatShortFileSize(context, bytes.coerceAtLeast(0L))

    private fun formatDuration(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        val hours = safe / 3_600
        val minutes = (safe % 3_600) / 60
        val remainder = safe % 60
        return when {
            hours > 0 -> "%d:%02d:%02d".format(hours, minutes, remainder)
            else -> "%d:%02d".format(minutes, remainder)
        }
    }

    companion object {
        const val CHANNEL_INSTALL = "install"
        const val CHANNEL_EVENTS = "events"
        const val INSTALL_NOTIFICATION_ID = 1001
        const val EVENT_NOTIFICATION_ID = 1002
        private const val PENDING_INTENT_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val install = NotificationChannel(
                CHANNEL_INSTALL,
                context.getString(R.string.notification_channel_install),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_channel_install_description)
                setSound(null, null)
                enableVibration(false)
            }
            val events = NotificationChannel(
                CHANNEL_EVENTS,
                context.getString(R.string.notification_channel_events),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_events_description)
            }
            manager.createNotificationChannels(listOf(install, events))
        }
    }
}
