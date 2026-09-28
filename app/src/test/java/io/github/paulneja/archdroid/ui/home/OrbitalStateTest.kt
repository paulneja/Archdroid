package io.github.paulneja.archdroid.ui.home

import io.github.paulneja.archdroid.core.chroot.ChrootStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class OrbitalStateTest {
    @Test fun `unmounted rootfs stays dormant even with stale activity`() {
        assertEquals(OrbitalState.DORMANT, orbitalState(ChrootStatus.INSTALLED, false, false, true))
    }
    @Test fun `mounted rootfs distinguishes idle and work`() {
        assertEquals(OrbitalState.READY, orbitalState(ChrootStatus.MOUNTED, false, false, false))
        assertEquals(OrbitalState.ACTIVE, orbitalState(ChrootStatus.MOUNTED, false, false, true))
    }
    @Test fun `transition and error override running state`() {
        assertEquals(OrbitalState.WORKING, orbitalState(ChrootStatus.MOUNTED, true, true, true))
        assertEquals(OrbitalState.ERROR, orbitalState(ChrootStatus.MOUNTED, false, true, true))
        assertEquals(OrbitalState.WORKING, orbitalState(ChrootStatus.UNKNOWN, false, false, false))
    }
}
