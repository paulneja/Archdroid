package io.github.paulneja.archdroid.core.terminal

/** Moves processes out of the app cgroup, which Android freezes and kills with the app. */
object ProcessShield {

    const val CGROUP = "/sys/fs/cgroup/archdroid"

    const val PROTECTED_OOM_SCORE = -1000
    const val DEFAULT_OOM_SCORE = 0

    /** For the first lines of a script, so everything it starts inherits the escape. */
    fun escapeSelfCommands(): List<String> = listOf(
        "mkdir -p $CGROUP 2>/dev/null",
        "read archdroid_pid _ < /proc/self/stat",
        "echo \$archdroid_pid > $CGROUP/cgroup.procs 2>/dev/null || true",
    )

    fun escapePidCommand(pid: Int): String {
        require(pid > 0) { "PID must be positive" }
        return "mkdir -p $CGROUP 2>/dev/null; echo $pid > $CGROUP/cgroup.procs 2>/dev/null || true"
    }

    fun oomCommand(pid: Int, score: Int): String {
        require(pid > 0) { "PID must be positive" }
        require(score in -1000..1000) { "oom_score_adj must be within -1000..1000" }
        return "echo $score > /proc/$pid/oom_score_adj"
    }
}
