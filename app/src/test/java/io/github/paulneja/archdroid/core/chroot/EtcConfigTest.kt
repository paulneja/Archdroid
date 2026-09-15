package io.github.paulneja.archdroid.core.chroot

import org.junit.Assert.assertTrue
import org.junit.Test

class EtcConfigTest {

    private val c = "/data/local/arch"

    @Test
    fun `resolv conf is removed before being written`() {
        // The Arch rootfs ships /etc/resolv.conf as a symlink to a systemd runtime path.
        // There is no systemd in a chroot, so it dangles, and a redirect follows it and
        // fails trying to create the target instead of replacing the link.
        val cmds = EtcConfig.resolvConfCommands(c, "8.8.8.8", "1.1.1.1")
        val remove = cmds.indexOfFirst { it.startsWith("rm -f") }
        val write = cmds.indexOfFirst { it.contains("printf") }
        assertTrue("the dangling symlink must be removed first", remove in 0 until write)
    }

    @Test
    fun `both nameservers reach the file`() {
        val joined = EtcConfig.resolvConfCommands(c, "8.8.8.8", "1.1.1.1").joinToString("\n")
        assertTrue(joined.contains("8.8.8.8"))
        assertTrue(joined.contains("1.1.1.1"))
        assertTrue(joined.contains("$c/etc/resolv.conf"))
    }

    @Test
    fun `nameservers are quoted`() {
        val joined = EtcConfig.resolvConfCommands(c, "it's bad", "1.1.1.1").joinToString("\n")
        assertTrue(joined.contains("""'it'\''s bad'"""))
    }

    @Test
    fun `mtab points at the live kernel mount table`() {
        // Plenty of tools read /etc/mtab rather than /proc/mounts; the stock rootfs leaves
        // it pointing nowhere useful once systemd is out of the picture.
        val joined = EtcConfig.mtabCommands(c).joinToString("\n")
        assertTrue(joined.contains("/proc/self/mounts"))
        assertTrue(joined.contains("$c/etc/mtab"))
        assertTrue("must replace whatever is there", joined.contains("ln -sf"))
    }

    @Test
    fun `hostname is quoted and written`() {
        val joined = EtcConfig.hostnameCommands(c, "arch droid").joinToString("\n")
        assertTrue(joined.contains("'arch droid'"))
        assertTrue(joined.contains("$c/etc/hostname"))
    }
}
