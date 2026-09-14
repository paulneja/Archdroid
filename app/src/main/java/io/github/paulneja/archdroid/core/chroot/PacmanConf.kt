package io.github.paulneja.archdroid.core.chroot

/**
 * Makes pacman usable inside a chroot on Android.
 *
 * Since 7.0 pacman sandboxes downloads with Landlock and drops to the `alpm` user. No
 * Android kernel ships Landlock, so every `pacman -Sy` aborts with "restricting filesystem
 * access failed" before it fetches anything. Turning the sandbox off is the only way
 * through, and it has to stick in the config rather than live on a command line, or the
 * user's own pacman calls in the terminal fail the same way.
 */
object PacmanConf {

    private val OPTIONS = listOf("DisableSandboxFilesystem", "DisableSandboxSyscalls")

    fun androidCompatCommands(confPath: String): List<String> {
        val f = confPath
        val cmds = mutableListOf<String>()
        for (option in OPTIONS) {
            // Uncomment it where the stock config already carries it...
            cmds += "sed -i -E 's/^#[[:space:]]*($option)[[:space:]]*\$/\\1/' $f"
            // ...and otherwise put it right under [options], never at the end of the file,
            // where it would fall inside a repository section and be ignored.
            cmds += "grep -qE '^$option' $f || sed -i '0,/^\\[options\\]/s//&\\n$option/' $f"
        }
        // Switching to the sandbox user fails in the chroot; downloading as root is fine
        // here because the whole chroot is already root-owned.
        cmds += "sed -i -E 's/^(DownloadUser[[:space:]]*=)/#\\1/' $f"
        return cmds
    }
}
