package io.github.paulneja.archdroid.core.root

import io.github.paulneja.archdroid.core.chroot.UserSetup
import org.junit.Assert.assertEquals
import org.junit.Test

class RootScriptTest {
    private fun runJob(script: String): String {
        val process = ProcessBuilder("sh").redirectErrorStream(true).start()
        process.outputStream.bufferedWriter().use {
            it.write(RootScript.command(script))
            it.write("\nstatus=\$?; echo JOB_STATUS=\$status\necho NEXT_JOB_ALIVE\n")
        }
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output, 0, process.waitFor())
        return output
    }

    @Test fun `mounted migration completes without closing the root shell`() {
        assertEquals("JOB_STATUS=0\nNEXT_JOB_ALIVE\n", runJob(UserSetup.migrateLegacyUsersScript("/")))
    }

    @Test fun `exit preserves failure code and the next job`() {
        assertEquals("JOB_STATUS=7\nNEXT_JOB_ALIVE\n", runJob("exit 7"))
    }

    @Test fun `exec replaces only the script process`() {
        assertEquals("child\nJOB_STATUS=0\nNEXT_JOB_ALIVE\n", runJob("exec printf 'child\\n'"))
    }

    @Test fun `errexit cannot kill the shared shell`() {
        assertEquals("JOB_STATUS=1\nNEXT_JOB_ALIVE\n", runJob("set -e\nfalse\necho unreachable"))
    }
}
