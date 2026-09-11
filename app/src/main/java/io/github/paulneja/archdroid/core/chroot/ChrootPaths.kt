package io.github.paulneja.archdroid.core.chroot

object ChrootPaths {
    const val CHROOT_DIR = "/data/local/arch"
    const val ENTER_SCRIPT = "$CHROOT_DIR/enter.sh"
    const val MARKER_FILE = "$CHROOT_DIR/etc/arch-release"
    const val RESOLV_CONF = "$CHROOT_DIR/etc/resolv.conf"

    // Plain http on purpose: os.archlinuxarm.org serves the rootfs but presents a
    // certificate for archlinuxarm.org only, so https fails hostname verification on
    // every device. This host geo-redirects to the nearest mirror, and the download is
    // checked against the published md5 once it lands.
    const val DEFAULT_MIRROR = "http://os.archlinuxarm.org"
    const val ROOTFS_PATH = "/os/ArchLinuxARM-aarch64-latest.tar.gz"

    fun rootfsUrl(mirror: String): String = mirror.trimEnd('/') + ROOTFS_PATH
    fun md5Url(mirror: String): String = rootfsUrl(mirror) + ".md5"
}
