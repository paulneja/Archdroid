package io.github.paulneja.archdroid.core.chroot

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCheckTest {

    @Test
    fun `accepts an archive made with tar -C rootfs dot`() {
        BackupCheck.requireRootfs(listOf("./etc/arch-release"))
    }

    @Test
    fun `accepts an archive without the leading dot`() {
        BackupCheck.requireRootfs(listOf("etc/arch-release"))
    }

    @Test
    fun `rejects an archive tar could not read to the end`() {
        assertThrows(InvalidBackupException::class.java) {
            BackupCheck.requireRootfs(listOf("./etc/arch-release", BackupCheck.TAR_FAILED))
        }
    }

    @Test
    fun `rejects an archive that is not a rootfs`() {
        assertThrows(InvalidBackupException::class.java) { BackupCheck.requireRootfs(emptyList()) }
    }

    @Test
    fun `the listing reads the whole archive and quotes the path`() {
        val script = BackupCheck.script("/data/x/it's.tar.gz")
        assertTrue(script.contains("-tzf '/data/x/it'\\''s.tar.gz'"))
        assertTrue("grep -q would stop tar early", !script.contains("grep -q"))
    }
}
