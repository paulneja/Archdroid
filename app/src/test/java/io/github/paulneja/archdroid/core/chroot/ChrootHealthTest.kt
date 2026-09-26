package io.github.paulneja.archdroid.core.chroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChrootHealthTest {

    private val c = "/data/local/arch"

    @Test
    fun `a normal login needs the shell, the user database and su`() {
        val paths = ChrootHealth.requiredPaths(c, loginAsRoot = false)
        assertTrue(paths.contains("$c/etc/arch-release"))
        assertTrue(paths.contains("$c/bin/bash"))
        assertTrue(paths.contains("$c/etc/passwd"))
        assertTrue(paths.contains("$c/bin/su"))
    }

    @Test
    fun `a root login does not need su`() {
        val paths = ChrootHealth.requiredPaths(c, loginAsRoot = true)
        assertFalse(paths.contains("$c/bin/su"))
        assertTrue(paths.contains("$c/bin/bash"))
    }

    @Test
    fun `the probe prints back only the paths that are missing`() {
        val script = ChrootHealth.probeScript(listOf("/a", "/b"))
        assertEquals(
            "test -e '/a' || echo '/a'\ntest -e '/b' || echo '/b'",
            script,
        )
    }

    @Test
    fun `the probe quotes paths with awkward characters`() {
        val script = ChrootHealth.probeScript(listOf("/it's here"))
        assertTrue(script.contains("""'/it'\''s here'"""))
    }

    @Test
    fun `a chroot with nothing missing is usable`() {
        assertTrue(ChrootHealth.isUsable(emptyList()))
    }

    @Test
    fun `a chroot missing its shell is not usable`() {
        assertFalse(ChrootHealth.isUsable(listOf("$c/bin/bash")))
    }

    @Test
    fun `the probe ignores blank lines coming back from the shell`() {
        assertEquals(
            listOf("$c/bin/bash"),
            ChrootHealth.missingFrom(listOf("", "  ", "$c/bin/bash")),
        )
    }

    @Test
    fun `the probe reports a login user missing from passwd`() {
        val script = ChrootHealth.userProbe(c, "arch")
        assertTrue(script.contains("grep -q '^arch:' '$c/etc/passwd'"))
        assertTrue(script.contains("|| echo 'user:arch'"))
    }

    private val passwd = listOf(
        "root:x:0:0::/root:/usr/bin/bash",
        "nobody:x:65534:65534:Kernel Overflow User:/:/usr/bin/nologin",
        "alarm:x:1000:1000::/home/alarm:/bin/bash",
        "paulneja:x:1001:1001::/home/paulneja:/bin/bash",
        "git:x:968:968:git daemon user:/:/usr/bin/git-shell",
    )

    @Test
    fun `adopts the human account the installer created, not the image default`() {
        assertEquals("paulneja", ChrootHealth.existingLoginUser(passwd))
    }

    @Test
    fun `falls back to alarm only when it is the sole human account`() {
        assertEquals("alarm", ChrootHealth.existingLoginUser(passwd.filterNot { it.startsWith("paulneja") }))
    }

    @Test
    fun `ignores system and nologin accounts`() {
        assertEquals(null, ChrootHealth.existingLoginUser(listOf(passwd[0], passwd[1], passwd[4])))
    }
}
