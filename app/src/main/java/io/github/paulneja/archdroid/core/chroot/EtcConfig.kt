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
}
