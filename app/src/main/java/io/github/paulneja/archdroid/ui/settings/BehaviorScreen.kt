package io.github.paulneja.archdroid.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.Backdrop
import io.github.paulneja.archdroid.R
import io.github.paulneja.archdroid.ui.components.GlassCard

@Composable
fun BehaviorScreen(
    backdrop: Backdrop,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val behavior = settings.behavior

    SettingsSubScreen(backdrop, stringResource(R.string.settings_behavior), onBack) {
        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            SwitchRow(
                stringResource(R.string.behavior_autostart),
                behavior.autostartOnBoot,
                backdrop,
            ) { viewModel.updateBehavior(behavior.copy(autostartOnBoot = it)) }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.behavior_autostart_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.behavior_scripts_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = behavior.startupScript,
                onValueChange = { viewModel.updateBehavior(behavior.copy(startupScript = it)) },
                label = { Text(stringResource(R.string.behavior_startup_script)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = behavior.shutdownScript,
                onValueChange = { viewModel.updateBehavior(behavior.copy(shutdownScript = it)) },
                label = { Text(stringResource(R.string.behavior_shutdown_script)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}
