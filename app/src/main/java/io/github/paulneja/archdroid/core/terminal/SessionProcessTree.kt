package io.github.paulneja.archdroid.core.terminal

import io.github.paulneja.archdroid.core.root.RootManager

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

    fun identityReadCommand(pid: Int): String {
        require(pid > 0) { "PID must be positive" }
        return "cat /proc/$pid/stat"
    }

    suspend fun readIdentity(pid: Int): ProcessIdentity {
        val statLine = RootManager.outputOf(identityReadCommand(pid)).singleOrNull()
            ?: throw IllegalStateException("could not read /proc/$pid/stat as root")
        return identity(statLine).also {
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
            read_stat() {
              stat=$d{1}
              [ -r "$d{stat}" ] || return 1
              IFS= read -r line < "$d{stat}" || return 1
              STAT_PID=$d{line%% *}; rest=$d{line##*) }; set -- $d{rest}
              STAT_SID=$d{4:-}; STAT_START=$d{20:-}
            }
            read_member() {
              pid=$d{1}; expected_start=$d{2}; stat=/proc/$d{pid}/stat
              read_stat "$d{stat}" || return 1
              [ "$d{STAT_PID}" = "$d{pid}" ] || return 1
              [ "$d{STAT_SID}" = "$d{LEADER}" ] || return 1
              [ "$d{STAT_START}" = "$d{expected_start}" ] || return 1
            }
            session_members() {
              for stat in /proc/[0-9]*/stat; do
                read_stat "$d{stat}" || continue
                pid=$d{STAT_PID}; sid=$d{STAT_SID}; start=$d{STAT_START}
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
            kill_members() {
              members=$d(session_members)
              for member in $d{members}; do [ "$d{member%%:*}" = "$d{LEADER}" ] || signal_one KILL "$d{member}"; done
              for member in $d{members}; do [ "$d{member%%:*}" = "$d{LEADER}" ] && signal_one KILL "$d{member}"; done
            }
            kill_members
            kill_members
            :
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
