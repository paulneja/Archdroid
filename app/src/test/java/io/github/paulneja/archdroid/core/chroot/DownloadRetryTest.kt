package io.github.paulneja.archdroid.core.chroot

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class DownloadRetryTest {

    @Test
    fun `succeeds after two network failures with exponential delays`() = runTest {
        var calls = 0
        val delays = mutableListOf<Long>()

        val result = DownloadRetry(delayer = { delays += it }).run(initialBytes = 0) {
            calls += 1
            if (calls <= 2) throw IOException("connection reset")
        }

        assertTrue(result.isSuccess)
        assertEquals(3, calls)
        assertEquals(listOf(2_000L, 4_000L), delays)
    }

    @Test
    fun `network failure retries five times then stops`() = runTest {
        var calls = 0
        val result = DownloadRetry(delayer = {}).run(initialBytes = 0) {
            calls += 1
            throw IOException("offline")
        }

        assertTrue(result.isFailure)
        assertEquals(5, calls)
    }

    @Test
    fun `download progress resets the consecutive failure budget`() = runTest {
        var calls = 0
        var partialBytes = 0L
        val result = DownloadRetry(delayer = {}).run(
            initialBytes = partialBytes,
            currentBytes = { partialBytes },
        ) {
            calls += 1
            when {
                calls <= 4 -> throw IOException("offline")
                calls == 5 -> {
                    partialBytes = 64 * 1024L
                    throw IOException("dropped after progress")
                }
                calls <= 8 -> throw IOException("offline again")
            }
        }

        assertTrue(result.isSuccess)
        assertEquals(9, calls)
    }

    @Test
    fun `http install errors are not retried`() = runTest {
        var calls = 0
        val result = DownloadRetry(delayer = {}).run(initialBytes = 0) {
            calls += 1
            throw ChrootInstaller.InstallException(ChrootInstaller.Step.DOWNLOAD, "HTTP 404")
        }

        assertTrue(result.isFailure)
        assertEquals(1, calls)
    }

    @Test
    fun `cancellation escapes unchanged`() = runTest {
        val cancellation = CancellationException("pause")

        try {
            DownloadRetry(delayer = {}).run(initialBytes = 0) { throw cancellation }
            fail("cancellation must escape")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }
}
