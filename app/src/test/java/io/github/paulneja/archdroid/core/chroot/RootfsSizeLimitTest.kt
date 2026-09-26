package io.github.paulneja.archdroid.core.chroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RootfsSizeLimitTest {
    @Test
    fun `accepts a known response that stays under the archive limit`() {
        assertEquals(
            900L,
            RootfsSizeLimit.totalBytes(resumedBytes = 400, responseBytes = 500),
        )
    }

    @Test
    fun `keeps an unknown response length unknown`() {
        assertEquals(-1L, RootfsSizeLimit.totalBytes(resumedBytes = 400, responseBytes = -1))
    }

    @Test
    fun `rejects declared and streamed sizes over the archive limit`() {
        assertThrows(RootfsTooLargeException::class.java) {
            RootfsSizeLimit.totalBytes(
                resumedBytes = RootfsSizeLimit.MAX_ARCHIVE_BYTES - 10,
                responseBytes = 11,
            )
        }
        assertThrows(RootfsTooLargeException::class.java) {
            RootfsSizeLimit.requireWithinLimit(RootfsSizeLimit.MAX_ARCHIVE_BYTES + 1)
        }
    }
}
