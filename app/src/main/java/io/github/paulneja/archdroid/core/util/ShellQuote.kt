package io.github.paulneja.archdroid.core.util

object ShellQuote {

    private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")

    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    fun isValidEnvName(name: String): Boolean = IDENTIFIER.matches(name)
}
