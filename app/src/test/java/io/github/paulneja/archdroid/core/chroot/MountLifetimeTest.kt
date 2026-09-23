package io.github.paulneja.archdroid.core.chroot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
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
