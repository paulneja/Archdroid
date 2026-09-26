package io.github.paulneja.archdroid.core.init

/** They would reconfigure the phone itself: the chroot shares its network, clock and firewall. */
object AndroidUnsafeUnits {
    val UNITS = listOf(
        // Network managers would take over wlan0 and rmnet interfaces.
        "systemd-networkd.service", "systemd-networkd.socket", "systemd-resolved.service",
        "NetworkManager.service", "dhcpcd.service", "dhcpcd@.service", "iwd.service",
        "wpa_supplicant.service", "wpa_supplicant@.service", "connman.service", "netctl.service",
        "ModemManager.service",
        "iptables.service", "ip6tables.service", "nftables.service", "firewalld.service", "ufw.service",
        // Android owns the clock.
        "systemd-timesyncd.service", "chronyd.service", "ntpd.service",
        // Hardware and consoles that belong to Android.
        "systemd-udevd.service", "systemd-udevd-control.socket", "systemd-udevd-kernel.socket",
        "bluetooth.service", "getty@.service", "serial-getty@.service",
        "systemd-logind.service", "systemd-remount-fs.service", "tmp.mount",
    )
}
