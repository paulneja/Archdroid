package io.github.paulneja.archdroid.core.notify

import io.github.paulneja.archdroid.core.chroot.ChrootInstaller
import io.github.paulneja.archdroid.core.service.InstallState

enum class InstallNotificationAction { PAUSE, RESUME, CANCEL, RETRY, OPEN_TERMINAL }

enum class InstallNotificationKind { IDLE, QUEUED, RUNNING, STOPPING, PAUSED, SUCCEEDED, FAILED, CANCELLED }

data class InstallNotificationModel(
    val kind: InstallNotificationKind,
    val step: ChrootInstaller.Step? = null,
    val detail: String = "",
    val message: String = "",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val bytesPerSecond: Long = 0L,
    val etaSeconds: Long = -1L,
    val progressPercent: Int? = null,
    val actions: List<InstallNotificationAction> = emptyList(),
    val ongoing: Boolean = false,
    val foreground: Boolean = false,
) {
    companion object {
        fun from(state: InstallState): InstallNotificationModel = when (state) {
            InstallState.Idle -> InstallNotificationModel(InstallNotificationKind.IDLE)
            InstallState.Queued -> InstallNotificationModel(
                kind = InstallNotificationKind.QUEUED,
                actions = listOf(InstallNotificationAction.PAUSE, InstallNotificationAction.CANCEL),
                ongoing = true,
                foreground = true,
            )
            is InstallState.Running -> InstallNotificationModel(
                kind = InstallNotificationKind.RUNNING,
                step = state.step,
                detail = state.detail,
                downloadedBytes = state.downloadedBytes,
                totalBytes = state.totalBytes,
                bytesPerSecond = state.bytesPerSecond,
                etaSeconds = state.etaSeconds,
                progressPercent = percent(state.downloadedBytes, state.totalBytes),
                actions = listOf(InstallNotificationAction.PAUSE, InstallNotificationAction.CANCEL),
                ongoing = true,
                foreground = true,
            )
            is InstallState.Stopping -> InstallNotificationModel(
                kind = InstallNotificationKind.STOPPING,
                step = state.running.step,
                detail = state.running.detail,
                downloadedBytes = state.running.downloadedBytes,
                totalBytes = state.running.totalBytes,
                progressPercent = percent(
                    state.running.downloadedBytes,
                    state.running.totalBytes,
                ),
                message = state.target.name,
                ongoing = true,
                foreground = true,
            )
            is InstallState.Paused -> InstallNotificationModel(
                kind = InstallNotificationKind.PAUSED,
                step = state.step,
                detail = state.detail,
                downloadedBytes = state.downloadedBytes,
                totalBytes = state.totalBytes,
                progressPercent = percent(state.downloadedBytes, state.totalBytes),
                actions = listOf(InstallNotificationAction.RESUME, InstallNotificationAction.CANCEL),
                ongoing = true,
            )
            InstallState.Succeeded -> InstallNotificationModel(
                kind = InstallNotificationKind.SUCCEEDED,
                actions = listOf(InstallNotificationAction.OPEN_TERMINAL),
            )
            is InstallState.Failed -> InstallNotificationModel(
                kind = InstallNotificationKind.FAILED,
                step = state.step,
                message = state.message.replace(Regex("\\s+"), " ").take(160),
                actions = listOf(InstallNotificationAction.RETRY),
            )
            InstallState.Cancelled -> InstallNotificationModel(InstallNotificationKind.CANCELLED)
        }

        private fun percent(downloadedBytes: Long, totalBytes: Long): Int? =
            if (totalBytes > 0) {
                ((downloadedBytes.coerceAtLeast(0L) * 100L) / totalBytes)
                    .coerceIn(0L, 100L)
                    .toInt()
            } else {
                null
            }
    }
}
