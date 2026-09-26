package io.github.paulneja.archdroid.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {

    @Test
    fun `an empty locale list means the system language`() {
        assertEquals(AppLanguage.Choice.SYSTEM, AppLanguage.fromTag(""))
    }

    @Test
    fun `regional tags map to their language`() {
        assertEquals(AppLanguage.Choice.SPANISH, AppLanguage.fromTag("es-AR"))
        assertEquals(AppLanguage.Choice.ENGLISH, AppLanguage.fromTag("en-US,es"))
    }

    @Test
    fun `unsupported languages fall back to the system`() {
        assertEquals(AppLanguage.Choice.SYSTEM, AppLanguage.fromTag("fr-FR"))
    }
}
