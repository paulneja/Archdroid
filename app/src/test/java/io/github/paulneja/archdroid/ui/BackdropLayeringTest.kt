package io.github.paulneja.archdroid.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
class BackdropLayeringTest {

    private val uiSources: List<File> =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/io/github/paulneja/archdroid/ui") }
            .first { it.isDirectory }
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    @Test
    fun `sources were actually found`() {
        assertTrue("no UI sources found to scan", uiSources.size > 10)
    }

    private val layerOwners = listOf("AppRoot.kt", "LiquidBottomTabs.kt")

    @Test
    fun `only AppRoot and the tab bar declare backdrop layers`() {
        val declaring = uiSources
            .filter { it.readText().contains("rememberLayerBackdrop") }
            .map { it.name }
            .sorted()
        assertEquals(layerOwners, declaring)
    }

    @Test
    fun `only AppRoot and the tab bar mark content as a layer source`() {
        val marking = uiSources
            .filter { it.readText().contains(".layerBackdrop(") }
            .map { it.name }
            .sorted()
        assertEquals(layerOwners, marking)
    }

    @Test
    fun `the tab bar pill is not inside the layer it samples`() {
        val bar = uiSources.first { it.name == "LiquidBottomTabs.kt" }.readText()
        val recorder = bar.indexOf(".layerBackdrop(tabsBackdrop)")
        val pill = bar.indexOf("rememberCombinedBackdrop(backdrop, tabsBackdrop)")

        val recorderEnd = Regex(
            """CompositionLocalProvider\(LocalContentColor provides accentColor\) \{ tabs\(false\) \}\s*\}\s*\}\s*Box\("""
        ).find(bar, recorder)?.range?.last ?: -1
        assertTrue(recorder in 0 until recorderEnd && pill > recorderEnd)
    }

    private val appRoot: String
        get() = uiSources.first { it.name == "AppRoot.kt" }.readText()

    @Test
    fun `the background and content layers stay separate`() {
        val declared = Regex("""val (\w+) = rememberLayerBackdrop""").findAll(appRoot)
            .map { it.groupValues[1] }.toSet()
        assertEquals(
            "one layer for the background and one for the content, no more, no less",
            setOf("backgroundBackdrop", "contentBackdrop"),
            declared,
        )
    }

    @Test
    fun `the background layer records an empty node`() {
        assertTrue(
            "the background layer source must stay an empty node",
            appRoot.contains("Box(Modifier.fillMaxSize().layerBackdrop(backgroundBackdrop))"),
        )
    }

    @Test
    fun `the layer recording the nav host is never handed to its screens`() {
        val givenToScreens = Regex("""\w+Screen\([^)]*backdrop\s*=\s*(\w+)""")
            .findAll(appRoot).map { it.groupValues[1] }.toSet()

        assertTrue("no screens found, the regex stopped matching", givenToScreens.isNotEmpty())
        assertEquals(
            "screens must sample the background layer, never the one that records them",
            setOf("backgroundBackdrop"),
            givenToScreens,
        )
    }
}
