package io.github.paulneja.archdroid.core.chroot

import io.github.paulneja.archdroid.core.util.ShellQuote.quote

/** Small /etc files the chroot needs that the stock rootfs leaves unusable. */
object EtcConfig {

    fun resolvConfCommands(chrootDir: String, dns1: String, dns2: String): List<String> {
        val path = "$chrootDir/etc/resolv.conf"
        return listOf(
            // The rootfs ships this as a symlink into /run/systemd, which never exists in
            // a chroot. Redirecting into a dangling symlink tries to create its target and
            // fails, so the link has to go first.
            "rm -f ${quote(path)}",
            "printf 'nameserver %s\\nnameserver %s\\n' ${quote(dns1)} ${quote(dns2)} > ${quote(path)}",
        )
    }

    /** Several tools consult /etc/mtab instead of /proc/mounts; point it somewhere real. */
    fun mtabCommands(chrootDir: String): List<String> =
        listOf("ln -sf /proc/self/mounts ${quote("$chrootDir/etc/mtab")}")

    fun hostnameCommands(chrootDir: String, hostname: String): List<String> =
        listOf("echo ${quote(hostname)} > ${quote("$chrootDir/etc/hostname")}")
}
