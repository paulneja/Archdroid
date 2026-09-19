package io.github.paulneja.archdroid.core.service

import io.github.paulneja.archdroid.core.chroot.ChrootInstaller

sealed interface InstallState {
    data object Idle : InstallState
    data object Queued : InstallState

    data class Running(
        val step: ChrootInstaller.Step,
        val detail: String = "",
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = -1L,
        val bytesPerSecond: Long = 0L,
        val etaSeconds: Long = -1L,
    ) : InstallState

    data class Paused(
        val step: ChrootInstaller.Step,
        val detail: String = "",
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = -1L,
    ) : InstallState

    data object Succeeded : InstallState

    data class Failed(
        val step: ChrootInstaller.Step?,
        val message: String,
    ) : InstallState

    data object Cancelled : InstallState
}
