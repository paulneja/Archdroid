package io.github.paulneja.archdroid.ui.terminal

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
class TerminalInsetsTest {

    private val screen: String =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java/io/github/paulneja/archdroid/ui/terminal/TerminalScreen.kt") }
            .first { it.isFile }
            .readText()

    @Test
    fun `terminal keeps its content clear of the navigation bar`() {
        assertTrue(
            "the extra-keys bar would sit under the system navigation bar",
            screen.contains("navigationBarsPadding()"),
        )
    }

    @Test
    fun `ime padding is applied before navigation bar padding`() {
        val ime = screen.indexOf("imePadding()")
        val nav = screen.indexOf("navigationBarsPadding()")
        assertTrue("imePadding() not found", ime >= 0)
        assertTrue("navigationBarsPadding() not found", nav >= 0)
        assertTrue("navigationBarsPadding() must come after imePadding()", nav > ime)
    }
}
