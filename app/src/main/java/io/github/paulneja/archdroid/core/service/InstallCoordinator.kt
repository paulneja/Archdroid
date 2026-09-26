package io.github.paulneja.archdroid.core.service

import io.github.paulneja.archdroid.core.chroot.ChrootInstaller
import io.github.paulneja.archdroid.data.model.InstallConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InstallCoordinator(
    private val scope: CoroutineScope,
    private val installer: suspend (
        InstallConfig,
        (ChrootInstaller.Progress) -> Unit,
        (String) -> Unit,
    ) -> Result<Unit>,
    private val onSuccess: suspend (InstallConfig) -> Unit = {},
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    private data class DownloadSample(val bytes: Long, val timeMillis: Long)

    private val lock = Any()
    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private var config: InstallConfig? = null
    private var installJob: Job? = null
    private var resumeWhenStopped = false
    private var lastRunning: InstallState.Running? = null
    private var downloadSample: DownloadSample? = null

    fun queue(config: InstallConfig) = synchronized(lock) {
        if (hasLiveJobLocked()) return@synchronized
        this.config = config
        resumeWhenStopped = false
        lastRunning = null
        downloadSample = null
        _log.value = emptyList()
        _state.value = InstallState.Queued
    }

    /** False after the process died: the password and choices only ever live in memory. */
    fun hasConfig(): Boolean = synchronized(lock) { config != null }

    fun startQueued() = synchronized(lock) {
        if (_state.value != InstallState.Queued || hasLiveJobLocked()) return@synchronized
        launchLocked()
    }

    fun pause() {
        val job = synchronized(lock) {
            if (_state.value !is InstallState.Running && _state.value != InstallState.Queued) {
                return
            }
            resumeWhenStopped = false
            val running = lastRunning ?: InstallState.Running(ChrootInstaller.Step.DOWNLOAD)
            installJob?.also {
                _state.value = InstallState.Stopping(InstallState.StopTarget.PAUSE, running)
            } ?: run {
                _state.value = InstallState.Paused(
                    running.step,
                    running.detail,
                    running.downloadedBytes,
                    running.totalBytes,
                )
                null
            }
        }
        job?.cancel(CancellationException("installation paused"))
    }

    fun resume() = synchronized(lock) {
        if (_state.value !is InstallState.Paused || config == null) return@synchronized
        if (hasLiveJobLocked()) {
            resumeWhenStopped = true
        } else {
            _state.value = InstallState.Queued
            launchLocked()
        }
    }

    fun cancel() {
        val job = synchronized(lock) {
            if (_state.value !is InstallState.Running &&
                _state.value !is InstallState.Paused &&
                _state.value != InstallState.Queued
            ) {
                return
            }
            resumeWhenStopped = false
            if (_state.value is InstallState.Paused && installJob == null) {
                _state.value = InstallState.Cancelled
                return@synchronized null
            }
            val running = lastRunning ?: InstallState.Running(ChrootInstaller.Step.DOWNLOAD)
            _state.value = InstallState.Stopping(InstallState.StopTarget.CANCEL, running)
            installJob
        }
        job?.cancel(CancellationException("installation cancelled"))
    }

    fun retry() = synchronized(lock) {
        if ((_state.value !is InstallState.Failed && _state.value != InstallState.Cancelled) ||
            config == null || hasLiveJobLocked()
        ) {
            return@synchronized
        }
        lastRunning = null
        downloadSample = null
        _state.value = InstallState.Queued
        launchLocked()
    }

    fun acknowledgeTerminalState() = synchronized(lock) {
        if (_state.value == InstallState.Succeeded || _state.value == InstallState.Cancelled) {
            config = null
            lastRunning = null
            downloadSample = null
            _log.value = emptyList()
            _state.value = InstallState.Idle
        }
    }

    private fun launchLocked() {
        val installConfig = config ?: return
        lastRunning = InstallState.Running(ChrootInstaller.Step.DOWNLOAD)
        downloadSample = null
        _state.value = lastRunning!!

        lateinit var newJob: Job
        newJob = scope.launch(start = CoroutineStart.LAZY) { execute(installConfig) }
        installJob = newJob
        newJob.invokeOnCompletion {
            synchronized(lock) {
                if (installJob === newJob) installJob = null
                if (resumeWhenStopped && _state.value is InstallState.Paused) {
                    resumeWhenStopped = false
                    _state.value = InstallState.Queued
                    launchLocked()
                }
            }
        }
        newJob.start()
    }

    private suspend fun execute(installConfig: InstallConfig) {
        try {
            val result = installer(installConfig, ::onProgress, ::appendLog)
            val failure = result.exceptionOrNull()
            if (failure != null) {
                publishFailure(failure)
                return
            }

            val mayComplete = synchronized(lock) { _state.value is InstallState.Running }
            if (!mayComplete) return
            onSuccess(installConfig)
            synchronized(lock) {
                if (_state.value is InstallState.Running) _state.value = InstallState.Succeeded
            }
        } catch (cancellation: CancellationException) {
            completeStopIfNeeded()
        } catch (failure: Throwable) {
            publishFailure(failure)
        } finally {
            completeStopIfNeeded()
        }
    }

    private fun completeStopIfNeeded() = synchronized(lock) {
        val stopping = _state.value as? InstallState.Stopping ?: return@synchronized
        _state.value = when (stopping.target) {
            InstallState.StopTarget.PAUSE -> InstallState.Paused(
                step = stopping.running.step,
                detail = stopping.running.detail,
                downloadedBytes = stopping.running.downloadedBytes,
                totalBytes = stopping.running.totalBytes,
            )
            InstallState.StopTarget.CANCEL -> InstallState.Cancelled
        }
    }

    private fun onProgress(progress: ChrootInstaller.Progress) = synchronized(lock) {
        if (_state.value !is InstallState.Running) return@synchronized
        val now = clockMillis()
        var bytesPerSecond = 0L
        var etaSeconds = -1L
        if (progress.step == ChrootInstaller.Step.DOWNLOAD) {
            val previous = downloadSample
            if (previous != null && progress.downloadedBytes >= previous.bytes && now > previous.timeMillis) {
                bytesPerSecond = (progress.downloadedBytes - previous.bytes) * 1_000L /
                    (now - previous.timeMillis)
                if (bytesPerSecond > 0 && progress.totalBytes >= progress.downloadedBytes) {
                    val remaining = progress.totalBytes - progress.downloadedBytes
                    etaSeconds = (remaining + bytesPerSecond - 1) / bytesPerSecond
                }
            }
            downloadSample = DownloadSample(progress.downloadedBytes, now)
        } else {
            downloadSample = null
        }

        lastRunning = InstallState.Running(
            step = progress.step,
            detail = progress.detail,
            downloadedBytes = progress.downloadedBytes,
            totalBytes = progress.totalBytes,
            bytesPerSecond = bytesPerSecond,
            etaSeconds = etaSeconds,
        )
        _state.value = lastRunning!!
        appendLogLocked(
            buildString {
                append(progress.step.name)
                if (progress.detail.isNotBlank()) append(": ${progress.detail}")
            }
        )
    }

    private fun publishFailure(failure: Throwable) = synchronized(lock) {
        if (_state.value !is InstallState.Running && _state.value != InstallState.Queued) {
            return@synchronized
        }
        val message = failure.message ?: failure.toString()
        val step = (failure as? ChrootInstaller.InstallException)?.step ?: lastRunning?.step
        appendLogLocked("ERROR: $message")
        _state.value = InstallState.Failed(step, message)
    }

    private fun appendLog(line: String) = synchronized(lock) { appendLogLocked(line) }

    private fun appendLogLocked(line: String) {
        _log.value = (_log.value + line).takeLast(MAX_LOG_LINES)
    }

    private fun hasLiveJobLocked(): Boolean = installJob?.isCompleted == false

    private companion object {
        const val MAX_LOG_LINES = 200
    }
}
