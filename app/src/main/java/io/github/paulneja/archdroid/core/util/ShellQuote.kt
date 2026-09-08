package io.github.paulneja.archdroid.core.util

/**
 * Single-quoting for values that end up inside root shell scripts. Everything the user can
 * type — usernames, passwords, mount paths, env values — goes through here before it is
 * pasted into a command line.
 */
object ShellQuote {

    private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")

    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    fun isValidEnvName(name: String): Boolean = IDENTIFIER.matches(name)
}
