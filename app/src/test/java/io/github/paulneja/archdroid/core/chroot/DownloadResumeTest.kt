package io.github.paulneja.archdroid.core.chroot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadResumeTest {

    @Test
    fun `a 416 for a partial download means every byte is already here`() {
        assertTrue(DownloadResume.partialIsComplete(416, 700_000_000L))
    }

    @Test
    fun `a 416 without a partial file is a real error`() {
        assertFalse(DownloadResume.partialIsComplete(416, 0L))
    }

    @Test
    fun `other responses are handled normally`() {
        assertFalse(DownloadResume.partialIsComplete(206, 10L))
        assertFalse(DownloadResume.partialIsComplete(200, 10L))
    }
}
