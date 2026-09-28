package io.github.paulneja.archdroid.core.terminal

/** Read only POSIX sessions owned by our terminals; an idle login shell is not activity. */
object SessionActivity {
    fun readScript(leaders: List<SessionProcessTree.ProcessIdentity>): String {
        require(leaders.isNotEmpty())
        val sessions = leaders.map { it.sessionId }.distinct().joinToString("|")
        return """
            for stat in /proc/[0-9]*/stat; do
              IFS= read -r line < "${'$'}stat" 2>/dev/null || continue
              rest=${'$'}{line##*) }; set -- ${'$'}rest
              case "${'$'}{4}" in $sessions) printf '%s\n' "${'$'}line";; esac
            done
        """.trimIndent()
    }

    fun hasWork(leaders: List<SessionProcessTree.ProcessIdentity>, lines: List<String>): Boolean {
        val liveSessions = leaders.filter { leader ->
            lines.any { runCatching { SessionProcessTree.identity(it) }.getOrNull() == leader }
        }.map { it.sessionId }.toSet()
        return lines.any { line ->
            val end = line.lastIndexOf(") ")
            if (end < 0) return@any false
            val fields = line.substring(end + 2).trim().split(Regex("\\s+"))
            val sid = fields.getOrNull(3)?.toIntOrNull()
            val name = line.substringAfter('(').substringBeforeLast(") ")
            sid in liveSessions && fields.firstOrNull() !in setOf("Z", "X") &&
                name !in setOf("su", "bash", "sh", "dash", "zsh", "fish", "mksh", "login")
        }
    }
}
