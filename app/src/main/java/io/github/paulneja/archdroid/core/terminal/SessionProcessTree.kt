package io.github.paulneja.archdroid.core.terminal

import io.github.paulneja.archdroid.core.root.RootManager
import java.io.File

object SessionProcessTree {
    data class ProcessIdentity(val pid: Int, val sessionId: Int, val startTimeTicks: Long) {
        init {
            require(pid > 0) { "PID must be positive" }
            require(sessionId > 0) { "session ID must be positive" }
            require(startTimeTicks > 0) { "start time must be positive" }
        }
    }

    fun identity(statLine: String): ProcessIdentity {
        val boundary = statLine.lastIndexOf(") ")
        require(boundary >= 0) { "invalid /proc stat record" }
        val pid = statLine.substringBefore(' ').toIntOrNull()
            ?: throw IllegalArgumentException("invalid PID")
        val tail = statLine.substring(boundary + 2).trim().split(Regex("\\s+"))
        val sessionId = tail.getOrNull(3)?.toIntOrNull()
            ?: throw IllegalArgumentException("invalid session ID")
        val startTime = tail.getOrNull(19)?.toLongOrNull()
            ?: throw IllegalArgumentException("invalid start time")
        return ProcessIdentity(pid, sessionId, startTime)
    }

    fun readIdentity(pid: Int): ProcessIdentity {
        require(pid > 0) { "PID must be positive" }
        return identity(File("/proc/$pid/stat").readText()).also {
            require(it.pid == pid) { "process identity changed while it was read" }
        }
    }

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

    fun members(leader: ProcessIdentity, statLines: List<String>): List<Int> =
        statLines.mapNotNull { line -> runCatching { identity(line) }.getOrNull() }
            .filter { candidate ->
                candidate.sessionId == leader.sessionId &&
                    (candidate.pid != leader.pid || candidate.startTimeTicks == leader.startTimeTicks)
            }
            .map { it.pid }

    fun script(leader: ProcessIdentity): String {
        require(leader.pid == leader.sessionId) { "terminal process must lead its POSIX session" }
        val d = '$'
        return """
            LEADER=${leader.pid}
            EXPECTED_LEADER_START=${leader.startTimeTicks}
            read_member() {
              pid=$d{1}; expected_start=$d{2}; stat=/proc/$d{pid}/stat
              [ -r "$d{stat}" ] || return 1
              line=$d(cat "$d{stat}" 2>/dev/null) || return 1
              current_pid=$d{line%% *}; rest=$d{line##*) }; set -- $d{rest}
              [ "$d{current_pid}" = "$d{pid}" ] || return 1
              [ "$d{4:-}" = "$d{LEADER}" ] || return 1
              [ "$d{20:-}" = "$d{expected_start}" ] || return 1
            }
            session_members() {
              for stat in /proc/[0-9]*/stat; do
                [ -r "$d{stat}" ] || continue
                line=$d(cat "$d{stat}" 2>/dev/null) || continue
                pid=$d{line%% *}; rest=$d{line##*) }; set -- $d{rest}
                sid=$d{4:-}; start=$d{20:-}
                [ "$d{sid}" = "$d{LEADER}" ] || continue
                if [ "$d{pid}" = "$d{LEADER}" ] && [ "$d{start}" != "$d{EXPECTED_LEADER_START}" ]; then continue; fi
                printf '%s:%s\n' "$d{pid}" "$d{start}"
              done
            }
            signal_one() {
              signal=$d{1}; member=$d{2}; pid=$d{member%%:*}; start=$d{member#*:}
              read_member "$d{pid}" "$d{start}" || return 0
              kill -"$d{signal}" "$d{pid}" 2>/dev/null || true
            }
            terminate_members() {
              members=$d(session_members)
              for member in $d{members}; do [ "$d{member%%:*}" = "$d{LEADER}" ] || signal_one TERM "$d{member}"; done
              for member in $d{members}; do [ "$d{member%%:*}" = "$d{LEADER}" ] && signal_one TERM "$d{member}"; done
            }
            kill_members() {
              members=$d(session_members)
              for member in $d{members}; do [ "$d{member%%:*}" = "$d{LEADER}" ] || signal_one KILL "$d{member}"; done
              for member in $d{members}; do [ "$d{member%%:*}" = "$d{LEADER}" ] && signal_one KILL "$d{member}"; done
            }
            terminate_members
            pass=0
            while [ $d{pass} -lt 5 ]; do
              [ -z "$d(session_members)" ] && break
              sleep 0.1
              pass=$d((pass + 1))
            done
            if [ -n "$d(session_members)" ]; then kill_members; sleep 0.1; fi
            [ -z "$d(session_members)" ]
        """.trimIndent()
    }

    suspend fun terminate(identity: ProcessIdentity): Result<Unit> = runCatching {
        val result = RootManager.execScript(script(identity))
        if (!result.isSuccess) {
            error(result.err.joinToString("\n").ifBlank {
                "processes in terminal session ${identity.sessionId} did not exit"
            })
        }
    }
}
