package io.github.paulneja.archdroid.core.chroot

import io.github.paulneja.archdroid.data.model.RootfsDownloadMode

object ChrootPaths {
    const val CHROOT_DIR = "/data/local/arch"
    const val ENTER_SCRIPT = "$CHROOT_DIR/enter.sh"
    const val MARKER_FILE = "$CHROOT_DIR/etc/arch-release"
    const val RESOLV_CONF = "$CHROOT_DIR/etc/resolv.conf"

    const val FAST_MIRROR = "http://os.archlinuxarm.org"
    const val SECURE_MIRROR = "https://fl.us.mirror.archlinuxarm.org"
    const val ROOTFS_PATH = "/os/ArchLinuxARM-aarch64-latest.tar.gz"

    fun rootfsUrl(mode: RootfsDownloadMode): String = rootfsUrl(mirror(mode))
    fun signatureUrl(): String = rootfsUrl(SECURE_MIRROR) + ".sig"

    fun mirror(mode: RootfsDownloadMode): String = when (mode) {
        RootfsDownloadMode.FAST -> FAST_MIRROR
        RootfsDownloadMode.SECURE -> SECURE_MIRROR
    }

    fun isAllowedEndpoint(mode: RootfsDownloadMode, scheme: String, host: String): Boolean {
        val officialHost = host == "archlinuxarm.org" || host.endsWith(".archlinuxarm.org")
        if (!officialHost) return false
        return when (mode) {
            RootfsDownloadMode.FAST -> scheme == "http" || scheme == "https"
            RootfsDownloadMode.SECURE -> scheme == "https"
        }
    }

    private fun rootfsUrl(mirror: String): String = mirror.trimEnd('/') + ROOTFS_PATH
}
