package io.github.paulneja.archdroid.core.chroot

/** Grants sudo to wheel through a drop-in file. */
object Sudoers {

    private const val DROP_IN = "/etc/sudoers.d/10-wheel"

    fun grantWheelCommands(): List<String> = listOf(
        "mkdir -p /etc/sudoers.d",
        "printf '%%wheel ALL=(ALL:ALL) ALL\\n' > $DROP_IN",
        "chmod 440 $DROP_IN",
    )
}
