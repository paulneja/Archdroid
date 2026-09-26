package io.github.paulneja.archdroid.data

import io.github.paulneja.archdroid.data.model.RootfsDownloadMode
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadModeSettingsTest {
    @Test
    fun `loads a stored download mode`() {
        assertEquals(
            RootfsDownloadMode.SECURE,
            DownloadModeSettings.decode("SECURE", null),
        )
    }

    @Test
    fun `migrates the old https mirror preference`() {
        assertEquals(
            RootfsDownloadMode.SECURE,
            DownloadModeSettings.decode(null, "https://mirror.example/rootfs.tar.gz"),
        )
        assertEquals(
            RootfsDownloadMode.FAST,
            DownloadModeSettings.decode(null, "http://mirror.example/rootfs.tar.gz"),
        )
    }

    @Test
    fun `falls back safely when a stored value is invalid`() {
        assertEquals(RootfsDownloadMode.FAST, DownloadModeSettings.decode("BROKEN", null))
    }
}
