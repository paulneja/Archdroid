package io.github.paulneja.archdroid.data.model

import org.junit.Assert.*
import org.junit.Test

class AppearanceSettingsTest {
    @Test fun `existing installs keep full effects by default`() {
        val defaults = AppSettings()
        assertFalse(defaults.performanceMode)
        assertEquals(VisualStyle.CHROME, defaults.visualStyle)
    }

    @Test fun `stored style names survive loading`() {
        VisualStyle.entries.forEach { assertEquals(it, VisualStyle.decode(it.name)) }
    }

    @Test fun `missing or unknown style uses the default`() {
        listOf(null, "", "OLD_STYLE", "aurora").forEach {
            assertEquals(VisualStyle.CHROME, VisualStyle.decode(it))
        }
    }

    @Test fun `performance preserves the chosen style for later`() {
        val settings = AppSettings(visualStyle = VisualStyle.EMBER)
        assertEquals(settings.visualStyle, settings.copy(performanceMode = true).visualStyle)
        assertFalse(settings.copy(performanceMode = true).copy(performanceMode = false).performanceMode)
    }
}
