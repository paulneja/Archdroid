package io.github.paulneja.archdroid.core.chroot

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException

class DownloadRetry(
    private val delayer: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun run(
        initialBytes: Long,
        currentBytes: () -> Long = { initialBytes },
        attempt: suspend (Long) -> Unit,
    ): Result<Unit> {
        var observedBytes = initialBytes.coerceAtLeast(0L)
        var consecutiveFailures = 0

        while (true) {
            try {
                attempt(observedBytes)
                return Result.success(Unit)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (network: IOException) {
                val latestBytes = currentBytes().coerceAtLeast(0L)
                if (latestBytes > observedBytes) consecutiveFailures = 0
                observedBytes = latestBytes
                consecutiveFailures += 1
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    return Result.failure(network)
                }
                delayer(BACKOFF_MILLIS[consecutiveFailures - 1])
            } catch (failure: Throwable) {
                return Result.failure(failure)
            }
        }
    }

    private companion object {
        const val MAX_CONSECUTIVE_FAILURES = 5
        val BACKOFF_MILLIS = longArrayOf(2_000L, 4_000L, 8_000L, 16_000L)
    }
}
