package io.github.paulneja.archdroid.core.service

import io.github.paulneja.archdroid.core.chroot.ChrootInstaller
import io.github.paulneja.archdroid.data.model.EnvConfig
import io.github.paulneja.archdroid.data.model.InstallConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InstallCoordinatorTest {

    private val config = InstallConfig(
        mirror = "https://example.invalid",
        packages = listOf("nano"),
        username = "arch",
        password = "secret",
        env = EnvConfig(),
    )

    private fun coordinator(
        scope: CoroutineScope,
        clockMillis: () -> Long = { 0L },
        onSuccess: suspend (InstallConfig) -> Unit = {},
        installer: suspend (
            InstallConfig,
            (ChrootInstaller.Progress) -> Unit,
            (String) -> Unit,
        ) -> Result<Unit>,
    ) = InstallCoordinator(scope, installer, onSuccess, clockMillis)

    @Test
    fun `duplicate start keeps one installation job`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var executions = 0
        val coordinator = coordinator(backgroundScope) { _, progress, _ ->
            executions += 1
            progress(
                ChrootInstaller.Progress(
                    ChrootInstaller.Step.DOWNLOAD,
                    downloadedBytes = 10,
                    totalBytes = 100,
                )
            )
            gate.await()
            Result.success(Unit)
        }

        coordinator.queue(config)
        coordinator.startQueued()
        coordinator.startQueued()
        runCurrent()

        assertEquals(1, executions)
        gate.complete(Unit)
    }

    @Test
    fun `pause preserves config and resume starts it again`() = runTest {
        var executions = 0
        val coordinator = coordinator(backgroundScope) { _, _, _ ->
            executions += 1
            awaitCancellation()
        }

        coordinator.queue(config)
        coordinator.startQueued()
        runCurrent()
        coordinator.pause()
        runCurrent()

        assertTrue(coordinator.state.value is InstallState.Paused)
        coordinator.resume()
        runCurrent()

        assertTrue(coordinator.state.value is InstallState.Running)
        assertEquals(2, executions)
    }

    @Test
    fun `cancel remains cancelled when installer cancellation returns`() = runTest {
        val coordinator = coordinator(backgroundScope) { _, _, _ -> awaitCancellation() }
        coordinator.queue(config)
        coordinator.startQueued()
        runCurrent()

        coordinator.cancel()
        runCurrent()

        assertEquals(InstallState.Cancelled, coordinator.state.value)
    }

    @Test
    fun `success runs completion and becomes succeeded`() = runTest {
        var completedWith: InstallConfig? = null
        val coordinator = coordinator(
            scope = backgroundScope,
            onSuccess = { completedWith = it },
        ) { _, _, _ -> Result.success(Unit) }

        coordinator.queue(config)
        coordinator.startQueued()
        runCurrent()

        assertEquals(config, completedWith)
        assertEquals(InstallState.Succeeded, coordinator.state.value)
    }

    @Test
    fun `installer failure preserves original step and message`() = runTest {
        val coordinator = coordinator(backgroundScope) { _, _, _ ->
            Result.failure(
                ChrootInstaller.InstallException(ChrootInstaller.Step.EXTRACT, "tar failed")
            )
        }

        coordinator.queue(config)
        coordinator.startQueued()
        runCurrent()

        val failed = coordinator.state.value as InstallState.Failed
        assertEquals(ChrootInstaller.Step.EXTRACT, failed.step)
        assertEquals("tar failed", failed.message)
    }

    @Test
    fun `retry from failure reuses config`() = runTest {
        var executions = 0
        val coordinator = coordinator(backgroundScope) { actualConfig, _, _ ->
            assertEquals(config, actualConfig)
            executions += 1
            if (executions == 1) Result.failure(IllegalStateException("first"))
            else Result.success(Unit)
        }

        coordinator.queue(config)
        coordinator.startQueued()
        runCurrent()
        assertTrue(coordinator.state.value is InstallState.Failed)

        coordinator.retry()
        runCurrent()

        assertEquals(2, executions)
        assertEquals(InstallState.Succeeded, coordinator.state.value)
    }

    @Test
    fun `download progress computes rate and eta from clock samples`() = runTest {
        var now = 1_000L
        val gate = CompletableDeferred<Unit>()
        val coordinator = coordinator(backgroundScope, clockMillis = { now }) { _, progress, _ ->
            progress(
                ChrootInstaller.Progress(
                    ChrootInstaller.Step.DOWNLOAD,
                    downloadedBytes = 100,
                    totalBytes = 1_100,
                )
            )
            now = 2_000L
            progress(
                ChrootInstaller.Progress(
                    ChrootInstaller.Step.DOWNLOAD,
                    downloadedBytes = 600,
                    totalBytes = 1_100,
                )
            )
            gate.await()
            Result.success(Unit)
        }

        coordinator.queue(config)
        coordinator.startQueued()
        runCurrent()

        val running = coordinator.state.value as InstallState.Running
        assertEquals(500L, running.bytesPerSecond)
        assertEquals(1L, running.etaSeconds)
        gate.complete(Unit)
    }

    @Test
    fun `log retains only the newest two hundred lines`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val coordinator = coordinator(backgroundScope) { _, _, log ->
            repeat(250) { log("line-$it") }
            gate.await()
            Result.success(Unit)
        }

        coordinator.queue(config)
        coordinator.startQueued()
        runCurrent()

        assertEquals(200, coordinator.log.value.size)
        assertEquals("line-50", coordinator.log.value.first())
        assertEquals("line-249", coordinator.log.value.last())
        gate.complete(Unit)
    }
}
