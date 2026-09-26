package io.github.paulneja.archdroid.core.protect

import io.github.paulneja.archdroid.core.util.ShellQuote.quote

/** Root commands behind the Battery & protection screen. */
object ProtectionCommands {

    const val UNLIMITED_PHANTOM = Int.MAX_VALUE

    val OOM_CHOICES = listOf(-900, -1000)

    /** Everything a user would otherwise grant by hand in several system screens. */
    fun backgroundAllowances(pkg: String): List<String> {
        val p = quote(pkg)
        return listOf(
            // The same allowlist as "Don't optimize" in the battery settings.
            "dumpsys deviceidle whitelist +$p",
            "cmd appops set $p RUN_IN_BACKGROUND allow",
            "cmd appops set $p RUN_ANY_IN_BACKGROUND allow",
            "am set-standby-bucket $p active",
        )
    }

    fun deviceIdleWhitelistCommand(): String = "dumpsys deviceidle whitelist"

    fun isWhitelisted(whitelistOutput: List<String>, pkg: String): Boolean =
        whitelistOutput.any { line -> line.split(',').map { it.trim() }.contains(pkg) }

    fun oomCommand(pid: Int, score: Int): String {
        require(pid > 0) { "PID must be positive" }
        require(score in -1000..1000) { "oom_score_adj must be within -1000..1000" }
        return "echo $score > /proc/$pid/oom_score_adj"
    }

    fun extendedOn(): List<String> = listOf(
        // Otherwise the next DeviceConfig sync from Google restores the default.
        "device_config set_sync_disabled_for_tests persistent",
        "device_config put activity_manager max_phantom_processes $UNLIMITED_PHANTOM",
        "settings put global settings_enable_monitor_phantom_procs false",
    )

    fun extendedOff(): List<String> = listOf(
        "device_config delete activity_manager max_phantom_processes",
        "settings delete global settings_enable_monitor_phantom_procs",
        "device_config set_sync_disabled_for_tests none",
    )

    fun extendedStateScript(): String =
        "echo \"max=$(device_config get activity_manager max_phantom_processes)\"; " +
            "echo \"monitor=$(settings get global settings_enable_monitor_phantom_procs)\""

    /** True only when both switches that limit child processes are lifted. */
    fun parseExtended(output: List<String>): Boolean {
        val values = output.mapNotNull { line ->
            line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() }
        }.toMap()
        val max = values["max"]?.toLongOrNull()
        val monitor = values["monitor"]
        return max == UNLIMITED_PHANTOM.toLong() && monitor == "false"
    }
}
