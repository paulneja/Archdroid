package io.github.paulneja.archdroid.core.init

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAccountsTest {

    @Test
    fun `only the published image passwords are checked`() {
        val script = DefaultAccounts.lockScript("/data/local/arch")
        assertTrue(script.contains("grep '^root:'"))
        assertTrue(script.contains("grep '^alarm:'"))
        assertTrue(script.contains("passwd -l root"))
        assertTrue(script.contains("passwd -l alarm"))
    }

    @Test
    fun `a password is only locked after crypt confirms it is the default`() {
        val script = DefaultAccounts.lockScript("/data/local/arch")
        val check = script.indexOf("crypt(")
        val lock = script.indexOf("passwd -l root")
        assertTrue(check in 0 until lock)
        assertFalse("the password must not be changed, only locked", script.contains("chpasswd"))
    }

    @Test
    fun `reports which accounts were locked`() {
        assertEquals(listOf("root"), DefaultAccounts.lockedFrom(listOf("locked root", "noise")))
    }
}
