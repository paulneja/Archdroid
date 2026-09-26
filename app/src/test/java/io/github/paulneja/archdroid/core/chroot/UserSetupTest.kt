package io.github.paulneja.archdroid.core.chroot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserSetupTest {

    @Test
    fun `the user joins wheel and the Android network groups`() {
        val cmd = UserSetup.ensureUserCommand("arch")
        assertTrue(cmd.contains("groupadd -g 3003 aid_inet"))
        assertTrue(cmd.contains("groupadd -g 3004 aid_net_raw"))
        assertTrue(cmd.contains("-m -G wheel -s /bin/bash 'arch'"))
        assertTrue(cmd.contains("usermod -aG wheel,"))
    }

    @Test
    fun `an existing user is not recreated`() {
        assertTrue(UserSetup.ensureUserCommand("arch").contains("id 'arch' >/dev/null 2>&1 ||"))
    }

    @Test
    fun `a mounted rootfs is not migrated`() {
        val process = ProcessBuilder("sh", "-c", UserSetup.migrateLegacyUsersScript("/"))
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        org.junit.Assert.assertEquals(output, 0, process.waitFor())
        org.junit.Assert.assertEquals("", output)
    }

    @Test
    fun `the password is piped from a builtin, never passed to chroot or bash`() {
        val script = UserSetup.setPasswordScript("/data/local/arch", "arch", "p'w d")
        val (producer, consumer) = script.split(" | ")
        assertTrue(producer.contains("'arch:p'\\''w d'"))
        assertFalse("password must not reach a process argv", consumer.contains("p'"))
        assertTrue(consumer.contains("chroot '/data/local/arch' /usr/bin/chpasswd"))
    }
}
