package io.github.paulneja.archdroid.core.chroot

import java.io.File

/** Files the installer keeps in the app sandbox between attempts. */
object InstallFiles {
    fun tarball(dir: File) = File(dir, "rootfs.tar.gz")
    fun partial(dir: File) = File(dir, "rootfs.tar.gz.part")
    fun signature(dir: File) = File(dir, "rootfs.tar.gz.sig")
    fun verification(dir: File) = File(dir, "rootfs.verified")
    fun checkpoint(dir: File) = File(dir, "install.checkpoint")

    fun all(dir: File): List<File> =
        listOf(tarball(dir), partial(dir), signature(dir), verification(dir), checkpoint(dir))
}
