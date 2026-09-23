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

    @Test
    fun `only AppRoot declares backdrop layers`() {
        val declaring = uiSources
            .filter { it.readText().contains("rememberLayerBackdrop") }
            .map { it.name }
            .sorted()
        assertEquals(listOf("AppRoot.kt"), declaring)
    }

    @Test
    fun `only AppRoot marks content as a layer source`() {
        val marking = uiSources
            .filter { it.readText().contains(".layerBackdrop(") }
            .map { it.name }
            .sorted()
        assertEquals(listOf("AppRoot.kt"), marking)
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
