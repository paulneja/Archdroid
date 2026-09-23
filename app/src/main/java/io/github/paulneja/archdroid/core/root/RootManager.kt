package io.github.paulneja.archdroid.core.root

import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor

object RootManager {

    suspend fun checkRoot(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Shell.getShell()
            Shell.isAppGrantedRoot() == true
        }.getOrDefault(false)
    }

    fun isRootGrantedSync(): Boolean = Shell.isAppGrantedRoot() == true

    suspend fun exec(vararg commands: String): Shell.Result = withContext(Dispatchers.IO) {
        Shell.cmd(*commands).to(ArrayList(), ArrayList()).exec()
    }

    suspend fun execScript(script: String): Shell.Result = withContext(Dispatchers.IO) {
        Shell.cmd(script).to(ArrayList(), ArrayList()).exec()
    }

    suspend fun execStreaming(
        script: String,
        onLine: (String) -> Unit,
    ): Shell.Result = withContext(Dispatchers.IO) {
        val sink = object : CallbackList<String>(Executor { it.run() }, ArrayList<String>()) {
            override fun onAddElement(line: String?) {
                line?.let(onLine)
            }
        }
        Shell.cmd(script).to(sink, sink).exec()
    }

    suspend fun test(command: String): Boolean = exec(command).isSuccess

    suspend fun outputOf(command: String): List<String> = exec(command).out

    suspend fun outputOfScript(script: String): List<String> = execScript(script).out
}
