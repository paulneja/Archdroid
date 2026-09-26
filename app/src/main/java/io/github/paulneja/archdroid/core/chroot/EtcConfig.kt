package io.github.paulneja.archdroid.core.chroot

import io.github.paulneja.archdroid.core.util.ShellQuote.quote

object EtcConfig {

    fun resolvConfCommands(chrootDir: String, dns1: String, dns2: String): List<String> {
        val path = "$chrootDir/etc/resolv.conf"
        return listOf(
            // The stock symlink points into systemd's unavailable /run tree.
            "rm -f ${quote(path)}",
            "printf 'nameserver %s\\nnameserver %s\\n' ${quote(dns1)} ${quote(dns2)} > ${quote(path)}",
        )
    }

    fun mtabCommands(chrootDir: String): List<String> =
        listOf("ln -sf /proc/self/mounts ${quote("$chrootDir/etc/mtab")}")

    fun hostnameCommands(chrootDir: String, hostname: String): List<String> =
        listOf("echo ${quote(hostname)} > ${quote("$chrootDir/etc/hostname")}")

    /** Follows Android's time zone so the chroot does not run in UTC. */
    fun localtimeCommands(chrootDir: String): List<String> {
        val zoneinfo = quote("$chrootDir/usr/share/zoneinfo")
        return listOf(
            "tz=$(getprop persist.sys.timezone); " +
                "[ -n \"${'$'}tz\" ] && [ -e $zoneinfo/\"${'$'}tz\" ] && " +
                "ln -sf \"/usr/share/zoneinfo/${'$'}tz\" ${quote("$chrootDir/etc/localtime")}; true"
        )
    }
}
