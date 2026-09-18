package io.github.paulneja.archdroid.core.terminal

import io.github.paulneja.archdroid.core.root.RootManager

object SessionProcessTree {

    fun members(leaderPid: Int, statLines: List<String>): List<Int> {
        require(leaderPid > 0) { "leader PID must be positive" }
        return statLines.mapNotNull { line ->
            val boundary = line.lastIndexOf(") ")
            if (boundary < 0) return@mapNotNull null
            val pid = line.substringBefore(' ').toIntOrNull() ?: return@mapNotNull null
            val tail = line.substring(boundary + 2).trim().split(Regex("\\s+"))
            val sessionId = tail.getOrNull(3)?.toIntOrNull() ?: return@mapNotNull null
            pid.takeIf { sessionId == leaderPid }
        }
    }

    fun script(leaderPid: Int): String {
        require(leaderPid > 0) { "leader PID must be positive" }
        val dollar = '$'
        return """
            LEADER=$leaderPid
            session_members() {
              for stat in /proc/[0-9]*/stat; do
                [ -r "$dollar{stat}" ] || continue
                line=$dollar(cat "$dollar{stat}" 2>/dev/null) || continue
                pid=$dollar{line%% *}
                rest=$dollar{line##*) }
                set -- $dollar{rest}
                [ "$dollar{4:-}" = "$dollar{LEADER}" ] && printf '%s\n' "$dollar{pid}"
              done
            }
            terminate_members() {
              members=$dollar(session_members)
              for pid in $dollar{members}; do
                [ "$dollar{pid}" = "$dollar{LEADER}" ] || kill -TERM "$dollar{pid}" 2>/dev/null || true
              done
              for pid in $dollar{members}; do
                [ "$dollar{pid}" = "$dollar{LEADER}" ] && kill -TERM "$dollar{pid}" 2>/dev/null || true
              done
            }
            kill_members() {
              members=$dollar(session_members)
              for pid in $dollar{members}; do
                [ "$dollar{pid}" = "$dollar{LEADER}" ] || kill -KILL "$dollar{pid}" 2>/dev/null || true
              done
              for pid in $dollar{members}; do
                [ "$dollar{pid}" = "$dollar{LEADER}" ] && kill -KILL "$dollar{pid}" 2>/dev/null || true
              done
            }
            terminate_members
            pass=0
            while [ $dollar{pass} -lt 5 ]; do
              [ -z "$dollar(session_members)" ] && exit 0
              sleep 0.1
              pass=$dollar((pass + 1))
            done
            kill_members
            sleep 0.1
            [ -z "$dollar(session_members)" ]
        """.trimIndent()
    }

    suspend fun terminate(leaderPid: Int): Result<Unit> = runCatching {
        val result = RootManager.execScript(script(leaderPid))
        if (!result.isSuccess) {
            val reason = result.err.joinToString("\n").ifBlank {
                "processes in terminal session $leaderPid did not exit"
            }
            error(reason)
        }
    }
}
