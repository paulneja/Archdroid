package io.github.paulneja.archdroid.core.chroot

object ChrootPaths {
    const val CHROOT_DIR = "/data/local/arch"
    const val ENTER_SCRIPT = "$CHROOT_DIR/enter.sh"
    const val MARKER_FILE = "$CHROOT_DIR/etc/arch-release"
    const val RESOLV_CONF = "$CHROOT_DIR/etc/resolv.conf"

    // This host has no matching TLS certificate; the archive is verified with its MD5.
    const val DEFAULT_MIRROR = "http://os.archlinuxarm.org"
    const val ROOTFS_PATH = "/os/ArchLinuxARM-aarch64-latest.tar.gz"

    fun rootfsUrl(mirror: String): String = mirror.trimEnd('/') + ROOTFS_PATH
    fun md5Url(mirror: String): String = rootfsUrl(mirror) + ".md5"
}
