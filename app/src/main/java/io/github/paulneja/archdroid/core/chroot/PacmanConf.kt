package io.github.paulneja.archdroid.core.chroot

object PacmanConf {

    private val OPTIONS = listOf("DisableSandboxFilesystem", "DisableSandboxSyscalls")

    private const val IGNORED = "linux-aarch64 linux-firmware linux-firmware-*"

    fun androidCompatCommands(confPath: String): List<String> {
        val f = confPath
        val cmds = mutableListOf<String>()

        cmds += "sed -i -E 's/^[[:space:]]*(CheckSpace)/#\\1/' $f"
        for (option in OPTIONS) {
            cmds += "sed -i -E 's/^#[[:space:]]*($option)[[:space:]]*\$/\\1/' $f"
            cmds += "grep -qE '^$option' $f || sed -i '/^\\[options\\]/a $option' $f"
        }
        cmds += "sed -i -E 's/^(DownloadUser[[:space:]]*=)/#\\1/' $f"

        cmds += "grep -qE '^IgnorePkg' $f || " +
            "sed -i '/^\\[options\\]/a IgnorePkg = $IGNORED' $f"
        return cmds
    }
}
