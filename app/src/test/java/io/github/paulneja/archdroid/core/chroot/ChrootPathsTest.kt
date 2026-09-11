package io.github.paulneja.archdroid.core.chroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChrootPathsTest {

    /**
     * os.archlinuxarm.org serves the rootfs but its certificate only covers
     * archlinuxarm.org and www.archlinuxarm.org, so an https default fails hostname
     * verification on every device. Upstream documents the plain-http host, and the
     * download is checked against the published md5 afterwards.
     */
    @Test
    fun `the default mirror is not the host with the mismatched certificate over https`() {
        assertFalse(
            "https://os.archlinuxarm.org cannot pass hostname verification",
            ChrootPaths.DEFAULT_MIRROR.startsWith("https://os."),
        )
    }

    @Test
    fun `the default mirror points at the arch linux arm network`() {
        assertTrue(ChrootPaths.DEFAULT_MIRROR.endsWith("archlinuxarm.org"))
    }

    @Test
    fun `builds the rootfs and md5 urls`() {
        assertEquals(
            "http://example.org/os/ArchLinuxARM-aarch64-latest.tar.gz",
            ChrootPaths.rootfsUrl("http://example.org"),
        )
        assertEquals(
            "http://example.org/os/ArchLinuxARM-aarch64-latest.tar.gz.md5",
            ChrootPaths.md5Url("http://example.org"),
        )
    }

    @Test
    fun `tolerates a trailing slash on the mirror`() {
        assertEquals(
            ChrootPaths.rootfsUrl("http://example.org"),
            ChrootPaths.rootfsUrl("http://example.org/"),
        )
    }
}
