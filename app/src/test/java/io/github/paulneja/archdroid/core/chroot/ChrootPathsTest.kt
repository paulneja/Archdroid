package io.github.paulneja.archdroid.core.chroot

import io.github.paulneja.archdroid.data.model.RootfsDownloadMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChrootPathsTest {
    @Test
    fun `fast mode uses the geo redirected http endpoint`() {
        assertEquals(
            "http://os.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz",
            ChrootPaths.rootfsUrl(RootfsDownloadMode.FAST),
        )
        assertEquals(
            "https://fl.us.mirror.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz.sig",
            ChrootPaths.signatureUrl(),
        )
    }

    @Test
    fun `secure mode uses the direct official https mirror`() {
        assertEquals(
            "https://fl.us.mirror.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz",
            ChrootPaths.rootfsUrl(RootfsDownloadMode.SECURE),
        )
    }

    @Test
    fun `fast mode accepts only official http or https endpoints`() {
        assertTrue(ChrootPaths.isAllowedEndpoint(RootfsDownloadMode.FAST, "http", "ca.us.mirror.archlinuxarm.org"))
        assertTrue(ChrootPaths.isAllowedEndpoint(RootfsDownloadMode.FAST, "https", "archlinuxarm.org"))
        assertFalse(ChrootPaths.isAllowedEndpoint(RootfsDownloadMode.FAST, "http", "archlinuxarm.org.evil.test"))
        assertFalse(ChrootPaths.isAllowedEndpoint(RootfsDownloadMode.FAST, "ftp", "archlinuxarm.org"))
    }

    @Test
    fun `secure mode rejects cleartext redirects and foreign hosts`() {
        assertTrue(ChrootPaths.isAllowedEndpoint(RootfsDownloadMode.SECURE, "https", "fl.us.mirror.archlinuxarm.org"))
        assertFalse(ChrootPaths.isAllowedEndpoint(RootfsDownloadMode.SECURE, "http", "fl.us.mirror.archlinuxarm.org"))
        assertFalse(ChrootPaths.isAllowedEndpoint(RootfsDownloadMode.SECURE, "https", "example.org"))
    }
}
