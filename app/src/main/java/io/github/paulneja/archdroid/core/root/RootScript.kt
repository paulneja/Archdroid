package io.github.paulneja.archdroid.core.root

/** Keeps exit, exec, errexit and shell variables inside one root job. */
object RootScript {
    fun command(script: String): String = "(\n$script\n)"
}
