package io.github.paulneja.archdroid.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.Backdrop
import io.github.paulneja.archdroid.R
import io.github.paulneja.archdroid.core.protect.ProtectionCommands
import io.github.paulneja.archdroid.ui.components.GlassCard
import io.github.paulneja.archdroid.ui.components.LiquidButton

@SuppressLint("BatteryLife") // Sideloaded root tool; the user asks for exactly this.
@Composable
fun ProtectionScreen(
    backdrop: Backdrop,
    onBack: () -> Unit,
    viewModel: ProtectionViewModel = viewModel(),
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val protection = settings.protection

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.grantAll() }

    LaunchedEffect(Unit) { viewModel.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    SettingsSubScreen(backdrop, stringResource(R.string.settings_protection), onBack) {
        error?.let { ApplyErrorCard(backdrop, it, viewModel::clearError) }

        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.protection_permissions_title))
            StatusRow(stringResource(R.string.protection_notifications), status.notifications)
            StatusRow(stringResource(R.string.protection_battery), status.batteryUnrestricted)
            StatusRow(stringResource(R.string.protection_background), status.backgroundAllowed)
            StatusRow(stringResource(R.string.protection_doze), status.idleWhitelisted)
            Spacer(Modifier.height(12.dp))
            LiquidButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !status.notifications) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.grantAll()
                    }
                },
                backdrop = backdrop,
                enabled = !busy,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.protection_grant_all), style = MaterialTheme.typography.titleSmall)
            }
            if (!status.batteryUnrestricted) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.protection_battery_manual),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                "package:${context.packageName}".toUri(),
                            )
                        )
                    },
                )
            }
        }

        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            SwitchRow(
                stringResource(R.string.behavior_terminal_survival),
                settings.behavior.terminalSurvival,
                backdrop,
                viewModel::setTerminalSurvival,
            )
            Hint(stringResource(R.string.behavior_terminal_survival_hint))
        }

        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            SwitchRow(
                stringResource(R.string.protection_oom),
                protection.oomEnabled,
                backdrop,
            ) { viewModel.updateProtection(protection.copy(oomEnabled = it)) }
            if (protection.oomEnabled) {
                Spacer(Modifier.height(8.dp))
                ProtectionCommands.OOM_CHOICES.forEach { score ->
                    ChoiceRow(
                        label = score.toString(),
                        selected = protection.oomScore == score,
                    ) { viewModel.updateProtection(protection.copy(oomScore = score)) }
                }
            }
            status.currentOom?.let {
                Spacer(Modifier.height(4.dp))
                Hint(stringResource(R.string.protection_oom_current, it))
            }
        }

        if (status.extendedSupported) {
            GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.protection_extended))
                Hint(stringResource(R.string.protection_extended_hint))
                Spacer(Modifier.height(12.dp))
                StatusRow(
                    stringResource(
                        if (status.extended) R.string.protection_extended_on else R.string.protection_extended_off
                    ),
                    status.extended,
                )
                Spacer(Modifier.height(12.dp))
                LiquidButton(
                    onClick = { viewModel.setExtended(!status.extended) },
                    backdrop = backdrop,
                    enabled = !busy,
                    tint = if (status.extended) MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (status.extended) R.string.protection_extended_disable
                            else R.string.protection_extended_enable
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Icon(
            if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
