package io.github.paulneja.archdroid.ui.settings

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.Backdrop
import io.github.paulneja.archdroid.R
import io.github.paulneja.archdroid.ui.components.GlassCard
import io.github.paulneja.archdroid.ui.components.LinearWavyProgressIndicator
import io.github.paulneja.archdroid.ui.components.LiquidButton
import io.github.paulneja.archdroid.ui.components.MotionAlertDialog

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BackupScreen(
    backdrop: Backdrop,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.backupState.collectAsStateWithLifecycle()
    var showResetConfirm by remember { mutableStateOf(false) }
    var showResetFinal by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    val busy = state is SettingsViewModel.BackupState.Running

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri -> uri?.let(viewModel::exportBackup) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importBackup) }

    SettingsSubScreen(backdrop, stringResource(R.string.settings_backup), onBack) {
        when (val s = state) {
            is SettingsViewModel.BackupState.Running -> {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
            is SettingsViewModel.BackupState.Success -> Text(
                stringResource(R.string.backup_done),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
            is SettingsViewModel.BackupState.Error -> Text(
                s.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            is SettingsViewModel.BackupState.MountsBusy -> GlassCard(
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.mounts_busy_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.mounts_busy_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                s.points.forEach { point ->
                    Text(
                        point,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            SettingsViewModel.BackupState.Idle -> {}
        }

        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.settings_backup),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.backup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            LiquidButton(
                onClick = { exportLauncher.launch("archdroid-backup.tar.gz") },
                backdrop = backdrop,
                enabled = !busy,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null)
                Text(
                    stringResource(R.string.backup_export),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            LiquidButton(
                onClick = { showImportConfirm = true },
                backdrop = backdrop,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Restore, contentDescription = null)
                Text(
                    stringResource(R.string.backup_import),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.reset_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.reset_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            LiquidButton(
                onClick = { showResetConfirm = true },
                backdrop = backdrop,
                enabled = !busy,
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.DeleteForever, contentDescription = null)
                Text(
                    stringResource(R.string.reset_button),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }

    if (showImportConfirm) {
        MotionAlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text(stringResource(R.string.backup_import_confirm_title)) },
            text = { Text(stringResource(R.string.backup_import_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    importLauncher.launch(arrayOf("application/gzip", "application/x-gzip", "application/octet-stream"))
                }) {
                    Text(stringResource(R.string.backup_import_confirm_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showResetConfirm) {
        MotionAlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.reset_title)) },
            text = { Text(stringResource(R.string.reset_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    showResetFinal = true
                }) {
                    Text(stringResource(R.string.reset_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showResetFinal) {
        MotionAlertDialog(
            onDismissRequest = { showResetFinal = false },
            title = { Text(stringResource(R.string.reset_confirm_final_title)) },
            text = { Text(stringResource(R.string.reset_confirm_final_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetFinal = false
                    viewModel.resetChroot()
                }) {
                    Text(
                        stringResource(R.string.reset_confirm_final_button),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetFinal = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
