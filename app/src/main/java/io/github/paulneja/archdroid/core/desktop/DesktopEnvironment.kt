package io.github.paulneja.archdroid.core.desktop

enum class DesktopEnvironment(
    val id: String,
    val displayName: String,

    val marker: String,
    val packages: List<String>,
    val waylandPackages: List<String>,
    val approxDownloadMb: Int,
) {
    KDE(
        id = "kde",
        displayName = "KDE Plasma 6",
        marker = "/usr/bin/startplasma-x11",

        packages = listOf(
            "plasma-desktop", "plasma-x11-session", "kwin-x11", "konsole", "dolphin",
            "breeze-gtk", "kde-gtk-config", "plasma-pa",
        ),
        waylandPackages = listOf("kwin", "xorg-xwayland"),
        approxDownloadMb = 900,
    ),
    XFCE(
        id = "xfce",
        displayName = "XFCE",
        marker = "/usr/bin/startxfce4",
        packages = listOf("xfce4", "xfce4-terminal"),
        waylandPackages = emptyList(),
        approxDownloadMb = 250,
    ),
    LXQT(
        id = "lxqt",
        displayName = "LXQt",
        marker = "/usr/bin/startlxqt",
        packages = listOf("lxqt", "openbox", "breeze-icons"),
        waylandPackages = listOf("lxqt-wayland-session", "labwc", "xorg-xwayland"),
        approxDownloadMb = 300,
    ),
    MATE(
        id = "mate",
        displayName = "MATE",
        marker = "/usr/bin/mate-session",
        packages = listOf("mate", "mate-terminal"),
        waylandPackages = emptyList(),
        approxDownloadMb = 300,
    );

    val supportsWayland: Boolean get() = waylandPackages.isNotEmpty()

    companion object {
        val COMMON_PACKAGES = listOf(
            "python", "xkeyboard-config", "xorg-xrandr", "xorg-xdpyinfo", "dbus", "mesa",
            "noto-fonts", "ttf-dejavu", "pipewire", "wireplumber", "pipewire-pulse",
            "xdg-user-dirs", "tigervnc",
        )

        fun fromId(id: String): DesktopEnvironment? = entries.firstOrNull { it.id == id }
    }
}

enum class SessionType { X11, WAYLAND }

enum class DisplayEngine { X11_NATIVE, VNC }
