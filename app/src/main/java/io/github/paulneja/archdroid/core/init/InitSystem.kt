package io.github.paulneja.archdroid.core.init

import android.content.Context
import io.github.paulneja.archdroid.core.chroot.ChrootPaths
import io.github.paulneja.archdroid.core.root.RootManager
import io.github.paulneja.archdroid.core.util.ShellQuote.quote
import io.github.paulneja.archdroid.data.model.InitMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** Brings services up and down according to the chosen [InitMode]. */
class InitSystem(private val context: Context) {

    private val c = ChrootPaths.CHROOT_DIR

    suspend fun capabilities(): KernelCapabilities =
        KernelCapabilities.parse(RootManager.outputOfScript(KernelCapabilities.PROBE_SCRIPT))

    /** Accounts locked by the last start because they still had a published password. */
    var lockedAccounts: List<String> = emptyList()
        private set

    suspend fun start(mode: InitMode) {
        lockedAccounts = DefaultAccounts.lockedFrom(RootManager.outputOfScript(DefaultAccounts.lockScript(c)))
        // A stale choice (e.g. a restored backup on another phone) falls back to compat.
        val effective = if (mode == InitMode.SYSTEMD && !capabilities().systemdSupported) InitMode.COMPAT else mode
        when (effective) {
            InitMode.COMPAT -> startCompat()
            InitMode.SYSTEMD -> startSystemd()
        }
    }

    suspend fun stop(mode: InitMode) {
        when (mode) {
            InitMode.COMPAT -> RootManager.execScript(CompatInit.haltCommand(c))
            InitMode.SYSTEMD -> pid1()?.let {
                RootManager.execScript(SystemdContainer.poweroffCommand(c, it))
                repeat(20) { if (pid1() == null) return; delay(500) }
            }
        }
    }

    suspend fun pid1(): String? =
        RootManager.outputOfScript(SystemdContainer.findPid1Script(c)).firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    private suspend fun startCompat() {
        val assets = extractAssets()
        val boot = File(context.filesDir, "archdroid-boot")
        withContext(Dispatchers.IO) { boot.writeText(CompatInit.bootScript()) }
        val bootTarget = quote("$c${CompatInit.BOOT_SCRIPT}")
        RootManager.exec(
            *(CompatInit.installCommands(c, assets.absolutePath) + CompatInit.maskCommands(c)).toTypedArray(),
            "mkdir -p ${quote("$c/usr/lib/archdroid")}",
            "cp ${quote(boot.absolutePath)} $bootTarget",
            "chmod 755 $bootTarget",
        )
        RootManager.execScript(CompatInit.bootCommand(c))
    }

    private suspend fun startSystemd() {
        if (pid1() != null) return
        RootManager.exec(*CompatInit.maskCommands(c).toTypedArray())
        RootManager.execScript(SystemdContainer.bootCommand(c))
        repeat(20) { if (pid1() != null) return; delay(500) }
    }

    private suspend fun extractAssets(): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "init").apply { mkdirs() }
        listOf("systemctl3.py", "journalctl3.py").forEach { name ->
            context.assets.open("init/$name").use { input ->
                File(dir, name).outputStream().use { input.copyTo(it) }
            }
        }
        dir
    }
}
