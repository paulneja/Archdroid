package io.github.paulneja.archdroid.core.init

data class KernelCapabilities(
    val pidNamespace: Boolean,
    val fhandle: Boolean?,
    val cgroup2: Boolean,
    val devtmpfs: Boolean,
    val sysvIpc: Boolean?,
    val configAvailable: Boolean,
) {
    val systemdSupported: Boolean get() = pidNamespace && fhandle == true && cgroup2

    data class Requirement(val name: String, val present: Boolean?, val requiredForSystemd: Boolean)

    fun requirements(): List<Requirement> = listOf(
        Requirement("CONFIG_PID_NS", pidNamespace, true),
        Requirement("CONFIG_FHANDLE", fhandle, true),
        Requirement("cgroup v2", cgroup2, true),
        Requirement("CONFIG_DEVTMPFS", devtmpfs, false),
        Requirement("CONFIG_SYSVIPC", sysvIpc, false),
    )

    companion object {
        /** Root script; every line is `key=value`. */
        const val PROBE_SCRIPT = "echo \"pidns=$([ -e /proc/self/ns/pid ] && echo 1 || echo 0)\"; " +
            "echo \"fs=$(grep -oE 'cgroup2|devtmpfs' /proc/filesystems | tr '\\n' ',')\"; " +
            "if [ -r /proc/config.gz ]; then echo config=1; " +
            "zcat /proc/config.gz 2>/dev/null | grep -E '^(# )?CONFIG_(FHANDLE|SYSVIPC)[ =]'; " +
            "else echo config=0; fi"

        fun parse(output: List<String>): KernelCapabilities {
            val values = output.mapNotNull { line ->
                line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() }
            }.toMap()
            val filesystems = values["fs"].orEmpty().split(',').filter { it.isNotBlank() }.toSet()
            val config = values["config"] == "1"
            fun option(name: String): Boolean? = when {
                !config -> null
                output.any { it.trim() == "CONFIG_$name=y" } -> true
                else -> false
            }
            return KernelCapabilities(
                pidNamespace = values["pidns"] == "1",
                fhandle = option("FHANDLE"),
                cgroup2 = "cgroup2" in filesystems,
                devtmpfs = "devtmpfs" in filesystems,
                sysvIpc = option("SYSVIPC"),
                configAvailable = config,
            )
        }
    }
}
