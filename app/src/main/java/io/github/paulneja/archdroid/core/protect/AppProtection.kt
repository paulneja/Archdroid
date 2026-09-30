package io.github.paulneja.archdroid.core.protect

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process
import androidx.core.app.NotificationManagerCompat
import io.github.paulneja.archdroid.core.root.RootManager
import io.github.paulneja.archdroid.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppProtection(
    private val context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    data class Status(
        val notifications: Boolean = false,
        val batteryUnrestricted: Boolean = false,
        val backgroundAllowed: Boolean = false,
        val idleWhitelisted: Boolean = false,
        val extended: Boolean = false,
        val extendedSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        val currentOom: Int? = null,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private var watchdog: Job? = null

    fun startWatchdog() {
        if (watchdog != null) return
        watchdog = scope.launch {
            settings.settings.map { it.protection }.distinctUntilChanged().collectLatest { cfg ->
                if (!cfg.oomEnabled) return@collectLatest
                while (isActive) {
                    RootManager.exec(ProtectionCommands.oomCommand(Process.myPid(), cfg.oomScore))
                    delay(WATCHDOG_MILLIS)
                }
            }
        }
    }

    suspend fun refresh() {
        val pkg = context.packageName
        val power = context.getSystemService(PowerManager::class.java)
        val activity = context.getSystemService(ActivityManager::class.java)
        val whitelist = RootManager.outputOf(ProtectionCommands.deviceIdleWhitelistCommand())
        val extended = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ProtectionCommands.parseExtended(RootManager.outputOfScript(ProtectionCommands.extendedStateScript()))
        } else {
            false
        }
        val oom = RootManager.outputOf("cat /proc/${Process.myPid()}/oom_score_adj").firstOrNull()
            ?.trim()?.toIntOrNull()
        _status.value = Status(
            notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            batteryUnrestricted = power.isIgnoringBatteryOptimizations(pkg),
            backgroundAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !activity.isBackgroundRestricted,
            idleWhitelisted = ProtectionCommands.isWhitelisted(whitelist, pkg),
            extended = extended,
            currentOom = oom,
        )
    }

    suspend fun grantBackgroundAllowances(): Result<Unit> = runCatching {
        val result = RootManager.exec(*ProtectionCommands.backgroundAllowances(context.packageName).toTypedArray())
        refresh()
        if (!result.isSuccess) error(result.err.joinToString("\n").ifBlank { "root command failed" })
    }

    suspend fun setExtended(enabled: Boolean): Result<Unit> = runCatching {
        val cmds = if (enabled) ProtectionCommands.extendedOn() else ProtectionCommands.extendedOff()
        val result = RootManager.exec(*cmds.toTypedArray())
        refresh()
        if (!result.isSuccess) error(result.err.joinToString("\n").ifBlank { "root command failed" })
    }

    private companion object {
        const val WATCHDOG_MILLIS = 10_000L
    }
}
