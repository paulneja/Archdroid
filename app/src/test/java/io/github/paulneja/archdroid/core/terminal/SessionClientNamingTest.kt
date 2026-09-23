package io.github.paulneja.archdroid.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
class SessionClientNamingTest {

    private fun source(name: String): String =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/io/github/paulneja/archdroid/core/terminal/$name") }
            .first { it.isFile }
            .readText()

    private fun constructorCallbacks(text: String): Set<String> {
        val header = text.substringAfter("class ").substringBefore(") :")
        return Regex("""val (\w+): \([^)]*\) -> """).findAll(header)
            .map { it.groupValues[1] }.toSet()
    }

    private fun overriddenNames(text: String): Set<String> =
        Regex("""override fun (\w+)\(""").findAll(text).map { it.groupValues[1] }.toSet()

    @Test
    fun `session client callbacks are not named after the methods they serve`() {
        val text = source("ArchTerminalSessionClient.kt")
        val callbacks = constructorCallbacks(text)
        assertTrue("no constructor callbacks found, the regex stopped matching", callbacks.isNotEmpty())
        assertEquals(
            "a callback sharing a name with an override makes the override call itself",
            emptySet<String>(),
            callbacks intersect overriddenNames(text),
        )
    }

    @Test
    fun `view client callbacks are not named after the methods they serve`() {
        val text = source("ArchTerminalViewClient.kt")
        assertEquals(
            emptySet<String>(),
            constructorCallbacks(text) intersect overriddenNames(text),
        )
    }
}
