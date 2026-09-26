package io.github.paulneja.archdroid.core.chroot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvValidationTest {

    @Test
    fun `hostnames`() {
        assertTrue(EnvValidation.isHostname("archdroid"))
        assertTrue(EnvValidation.isHostname("my-phone.lan"))
        assertFalse(EnvValidation.isHostname(""))
        assertFalse(EnvValidation.isHostname("-bad"))
        assertFalse(EnvValidation.isHostname("has space"))
    }

    @Test
    fun `dns servers must be addresses`() {
        assertTrue(EnvValidation.isDnsServer("8.8.8.8"))
        assertTrue(EnvValidation.isDnsServer("2606:4700:4700::1111"))
        assertFalse(EnvValidation.isDnsServer("8.8."))
        assertFalse(EnvValidation.isDnsServer("256.1.1.1"))
        assertFalse(EnvValidation.isDnsServer("dns.google"))
    }

    @Test
    fun `locales`() {
        assertTrue(EnvValidation.isLang("en_US.UTF-8"))
        assertTrue(EnvValidation.isLang("es_AR.UTF-8"))
        assertTrue(EnvValidation.isLang("C.UTF-8"))
        assertFalse(EnvValidation.isLang("en US"))
    }
}
