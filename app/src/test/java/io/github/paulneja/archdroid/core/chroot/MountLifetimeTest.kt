package io.github.paulneja.archdroid.core.chroot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mounts left up with nobody using them are what turns a stray `rm -rf` on the chroot
 * directory into a delete that reaches the real filesystem. They come down as soon as the
 * last session ends -- unless the user asked for them directly.
 */
class MountLifetimeTest {

    @Test
    fun `the last session leaving takes the mounts down with it`() {
        assertTrue(MountLifetime.shouldUnmount(pinnedByUser = false, remainingSessions = 0))
    }

    @Test
    fun `mounts stay while another session is still using them`() {
        assertFalse(MountLifetime.shouldUnmount(pinnedByUser = false, remainingSessions = 1))
    }

    @Test
    fun `a mount the user asked for is not taken down behind their back`() {
        assertFalse(MountLifetime.shouldUnmount(pinnedByUser = true, remainingSessions = 0))
    }

    @Test
    fun `pinning wins even with sessions open`() {
        assertFalse(MountLifetime.shouldUnmount(pinnedByUser = true, remainingSessions = 3))
    }
}
