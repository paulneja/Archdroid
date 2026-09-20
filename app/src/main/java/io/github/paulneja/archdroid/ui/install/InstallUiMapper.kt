package io.github.paulneja.archdroid.ui.install

import io.github.paulneja.archdroid.core.chroot.ChrootInstaller
import io.github.paulneja.archdroid.core.service.InstallState

object InstallUiMapper {
    fun apply(
        ui: InstallWizardViewModel.UiState,
        installState: InstallState,
        log: List<String>,
    ): InstallWizardViewModel.UiState = when (installState) {
        InstallState.Idle -> ui.copy(
            running = false,
            installState = installState,
            log = log,
        )
        InstallState.Queued -> ui.copy(
            page = 4,
            running = true,
            error = null,
            done = false,
            installState = installState,
            log = log,
        )
        is InstallState.Running -> ui.copy(
            page = 4,
            running = true,
            progress = installState.toProgress(),
            error = null,
            done = false,
            installState = installState,
            log = log,
        )
        is InstallState.Stopping -> ui.copy(
            page = 4,
            running = true,
            progress = installState.running.let {
                ChrootInstaller.Progress(it.step, it.detail, it.downloadedBytes, it.totalBytes)
            },
            error = null,
            done = false,
            installState = installState,
            log = log,
        )
        is InstallState.Paused -> ui.copy(
            page = 4,
            running = false,
            progress = ChrootInstaller.Progress(
                step = installState.step,
                detail = installState.detail,
                downloadedBytes = installState.downloadedBytes,
                totalBytes = installState.totalBytes,
            ),
            error = null,
            done = false,
            installState = installState,
            log = log,
        )
        InstallState.Succeeded -> ui.copy(
            page = 5,
            running = false,
            error = null,
            done = true,
            installState = installState,
            log = log,
        )
        is InstallState.Failed -> ui.copy(
            page = 4,
            running = false,
            progress = installState.step?.let { ChrootInstaller.Progress(it) } ?: ui.progress,
            error = installState.message,
            done = false,
            installState = installState,
            log = log,
        )
        InstallState.Cancelled -> ui.copy(
            page = 4,
            running = false,
            done = false,
            installState = installState,
            log = log,
        )
    }

    private fun InstallState.Running.toProgress() = ChrootInstaller.Progress(
        step = step,
        detail = detail,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
    )
}
