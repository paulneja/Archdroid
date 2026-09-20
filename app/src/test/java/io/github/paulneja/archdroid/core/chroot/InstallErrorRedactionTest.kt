package io.github.paulneja.archdroid.core.chroot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallErrorRedactionTest {

    @Test
    fun `password command failure reports only its safe label`() {
        val message = ChrootInstaller.commandFailure(
            label = "set password for arch",
            stderr = listOf("permission denied"),
        )

        assertTrue(message.contains("set password for arch"))
        assertTrue(message.contains("permission denied"))
        assertFalse(message.contains("chpasswd"))
        assertFalse(message.contains("hunter2"))
    }
}
