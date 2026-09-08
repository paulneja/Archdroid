package io.github.paulneja.archdroid.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellQuoteTest {

    @Test
    fun `wraps plain value in single quotes`() {
        assertEquals("'arch'", ShellQuote.quote("arch"))
    }

    @Test
    fun `escapes embedded single quote so the value cannot break out`() {
        assertEquals("""'it'\''s'""", ShellQuote.quote("it's"))
    }

    @Test
    fun `neutralises a command injection attempt`() {
        val evil = "x'; rm -rf /; echo '"
        val quoted = ShellQuote.quote(evil)
        assertEquals("""'x'\''; rm -rf /; echo '\'''""", quoted)
    }

    @Test
    fun `quotes empty string`() {
        assertEquals("''", ShellQuote.quote(""))
    }
}
