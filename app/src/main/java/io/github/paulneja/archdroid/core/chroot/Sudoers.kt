package io.github.paulneja.archdroid.core.chroot

/**
 * Gives the wheel group sudo through a drop-in rather than by editing /etc/sudoers.
 *
 * Patching the stock file means matching its exact wording; when that match fails the edit
 * is a silent no-op and the install still reports success, leaving a user who cannot run
 * anything as root. A drop-in has nothing to match and is what sudo documents.
 */
object Sudoers {

    private const val DROP_IN = "/etc/sudoers.d/10-wheel"

    fun grantWheelCommands(): List<String> = listOf(
        "mkdir -p /etc/sudoers.d",
        "printf '%%wheel ALL=(ALL:ALL) ALL\\n' > $DROP_IN",
        // sudo refuses to read a drop-in that anyone else could write to.
        "chmod 440 $DROP_IN",
    )
}
