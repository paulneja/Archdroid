package io.github.paulneja.archdroid.core.init

object AndroidUnsafeUnits {
    val UNITS = listOf(

        "systemd-networkd.service", "systemd-networkd.socket", "systemd-resolved.service",
        "NetworkManager.service", "dhcpcd.service", "dhcpcd@.service", "iwd.service",
        "wpa_supplicant.service", "wpa_supplicant@.service", "connman.service", "netctl.service",
        "ModemManager.service",
        "iptables.service", "ip6tables.service", "nftables.service", "firewalld.service", "ufw.service",

        "systemd-timesyncd.service", "chronyd.service", "ntpd.service",

        "systemd-udevd.service", "systemd-udevd-control.socket", "systemd-udevd-kernel.socket",
        "bluetooth.service", "getty@.service", "serial-getty@.service",
        "systemd-logind.service", "systemd-remount-fs.service", "tmp.mount",
    )
}
