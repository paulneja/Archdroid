package io.github.paulneja.archdroid.core.root

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RootManager {

    suspend fun checkRoot(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Shell.getShell()
            Shell.isAppGrantedRoot() == true
        }.getOrDefault(false)
    }

    fun isRootGrantedSync(): Boolean = Shell.isAppGrantedRoot() == true

    suspend fun exec(vararg commands: String): Shell.Result = withContext(Dispatchers.IO) {
        Shell.cmd(*commands).exec()
    }

    suspend fun execScript(script: String): Shell.Result = withContext(Dispatchers.IO) {
        Shell.cmd(script).exec()
    }

    suspend fun test(command: String): Boolean = exec(command).isSuccess

    suspend fun outputOf(command: String): List<String> = exec(command).out
}
