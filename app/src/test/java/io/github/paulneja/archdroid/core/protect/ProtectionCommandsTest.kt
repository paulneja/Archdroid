package io.github.paulneja.archdroid.core.protect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionCommandsTest {

    private val pkg = "io.github.paulneja.archdroid"

    @Test
    fun `background allowances cover doze, app ops and the standby bucket`() {
        val cmds = ProtectionCommands.backgroundAllowances(pkg).joinToString("\n")
        assertTrue(cmds.contains("dumpsys deviceidle whitelist +'$pkg'"))
        assertTrue(cmds.contains("cmd appops set '$pkg' RUN_ANY_IN_BACKGROUND allow"))
        assertTrue(cmds.contains("am set-standby-bucket '$pkg' active"))
    }

    @Test
    fun `whitelist parsing matches the package column exactly`() {
        val output = listOf(
            "system-excidle,com.android.vending,10129",
            "user,$pkg,10411",
        )
        assertTrue(ProtectionCommands.isWhitelisted(output, pkg))
        assertFalse(ProtectionCommands.isWhitelisted(output, "$pkg.debug"))
        assertFalse(ProtectionCommands.isWhitelisted(listOf("user,$pkg.debug,1"), pkg))
    }

    @Test
    fun `extended protection lifts both limits and pins them against config sync`() {
        val on = ProtectionCommands.extendedOn().joinToString("\n")
        assertTrue(on.contains("set_sync_disabled_for_tests persistent"))
        assertTrue(on.contains("max_phantom_processes 2147483647"))
        assertTrue(on.contains("settings_enable_monitor_phantom_procs false"))
    }

    @Test
    fun `turning it off deletes the overrides instead of guessing a default`() {
        val off = ProtectionCommands.extendedOff().joinToString("\n")
        assertTrue(off.contains("device_config delete activity_manager max_phantom_processes"))
        assertTrue(off.contains("settings delete global settings_enable_monitor_phantom_procs"))
        assertTrue(off.contains("set_sync_disabled_for_tests none"))
        assertFalse(off.contains("put"))
    }

    @Test
    fun `stock devices report extended protection as off`() {
        assertFalse(ProtectionCommands.parseExtended(listOf("max=null", "monitor=null")))
    }

    @Test
    fun `extended protection counts as on only when both switches are lifted`() {
        assertTrue(ProtectionCommands.parseExtended(listOf("max=2147483647", "monitor=false")))
        assertFalse(ProtectionCommands.parseExtended(listOf("max=2147483647", "monitor=null")))
        assertFalse(ProtectionCommands.parseExtended(listOf("max=64", "monitor=false")))
    }

    @Test
    fun `oom command targets the given pid`() {
        assertEquals("echo -900 > /proc/7/oom_score_adj", ProtectionCommands.oomCommand(7, -900))
    }
}
