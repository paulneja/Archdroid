package io.github.paulneja.archdroid.ui.terminal

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The extra-keys bar is the bottom-most row of the terminal, so without navigation bar
 * insets it ends up underneath Android's back/home/recents bar and the keys cannot be
 * tapped. This is a stated product requirement, not a nicety.
 */
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
        // Inset modifiers consume what they apply, so this order gives the keyboard inset
        // when the keyboard is up and the navigation bar inset when it is not, never both.
        val ime = screen.indexOf("imePadding()")
        val nav = screen.indexOf("navigationBarsPadding()")
        assertTrue("imePadding() not found", ime >= 0)
        assertTrue("navigationBarsPadding() not found", nav >= 0)
        assertTrue("navigationBarsPadding() must come after imePadding()", nav > ime)
    }
}
