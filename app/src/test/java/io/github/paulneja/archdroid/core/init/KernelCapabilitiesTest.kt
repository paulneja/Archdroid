package io.github.paulneja.archdroid.core.init

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KernelCapabilitiesTest {

    /** Output captured from an Armor 29 Ultra (GKI 6.1, android14). */
    private val stockGki = listOf(
        "pidns=0",
        "fs=cgroup2,",
        "config=1",
        "# CONFIG_SYSVIPC is not set",
        "# CONFIG_FHANDLE is not set",
    )

    @Test
    fun `a stock GKI kernel cannot run systemd as PID 1`() {
        val caps = KernelCapabilities.parse(stockGki)
        assertFalse(caps.pidNamespace)
        assertEquals(false, caps.fhandle)
        assertTrue(caps.cgroup2)
        assertFalse(caps.systemdSupported)
    }

    @Test
    fun `a kernel with pid namespaces, fhandle and cgroup2 can`() {
        val caps = KernelCapabilities.parse(
            listOf("pidns=1", "fs=cgroup2,devtmpfs,", "config=1", "CONFIG_FHANDLE=y", "CONFIG_SYSVIPC=y")
        )
        assertTrue(caps.systemdSupported)
        assertTrue(caps.devtmpfs)
    }

    @Test
    fun `without config gz fhandle is unknown and systemd is not offered`() {
        val caps = KernelCapabilities.parse(listOf("pidns=1", "fs=cgroup2,", "config=0"))
        assertNull(caps.fhandle)
        assertFalse(caps.systemdSupported)
    }

    @Test
    fun `requirements mark what systemd needs`() {
        val needed = KernelCapabilities.parse(stockGki).requirements().filter { it.requiredForSystemd }
        assertEquals(listOf("CONFIG_PID_NS", "CONFIG_FHANDLE", "cgroup v2"), needed.map { it.name })
    }
}
