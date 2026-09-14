package io.github.paulneja.archdroid.core.chroot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SudoersTest {

    private val cmds = Sudoers.grantWheelCommands()

    @Test
    fun `does not depend on the exact wording of the stock sudoers file`() {
        // A sed against a line that shifted by one character silently does nothing and
        // leaves the user with no sudo at all, with the install still reporting success.
        assertFalse(cmds.joinToString("\n").contains("sed"))
    }

    @Test
    fun `grants wheel through a drop-in file`() {
        val joined = cmds.joinToString("\n")
        assertTrue(joined.contains("/etc/sudoers.d/"))
        assertTrue(joined.contains("%wheel ALL=(ALL:ALL) ALL"))
    }

    @Test
    fun `the drop-in is locked down to the mode sudo demands`() {
        assertTrue(cmds.joinToString("\n").contains("chmod 440"))
    }

    @Test
    fun `writing the drop-in comes before locking it`() {
        val write = cmds.indexOfFirst { it.contains("%wheel") }
        val chmod = cmds.indexOfFirst { it.contains("chmod") }
        assertTrue(write in 0 until chmod)
    }
}
