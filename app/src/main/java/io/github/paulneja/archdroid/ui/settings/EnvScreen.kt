package io.github.paulneja.archdroid.ui.settings

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.Backdrop
import io.github.paulneja.archdroid.R
import io.github.paulneja.archdroid.core.chroot.EnvValidation
import io.github.paulneja.archdroid.ui.components.GlassCard
import io.github.paulneja.archdroid.ui.components.MotionIconButton

@Composable
fun EnvScreen(
    backdrop: Backdrop,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val env = settings.env

    SettingsSubScreen(backdrop, stringResource(R.string.settings_env), onBack) {
        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.env_system_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            DraftField(
                value = env.hostname,
                label = stringResource(R.string.env_hostname),
                onCommit = { viewModel.updateEnv(env.copy(hostname = it)) },
                isValid = EnvValidation::isHostname,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            DraftField(
                value = env.dnsPrimary,
                label = stringResource(R.string.env_dns1),
                onCommit = { viewModel.updateEnv(env.copy(dnsPrimary = it)) },
                isValid = EnvValidation::isDnsServer,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            DraftField(
                value = env.dnsSecondary,
                label = stringResource(R.string.env_dns2),
                onCommit = { viewModel.updateEnv(env.copy(dnsSecondary = it)) },
                isValid = EnvValidation::isDnsServer,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            DraftField(
                value = env.lang,
                label = stringResource(R.string.env_lang),
                onCommit = { viewModel.updateEnv(env.copy(lang = it)) },
                isValid = EnvValidation::isLang,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.env_extra_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            env.extraEnv.forEach { (key, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$key=$value",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    MotionIconButton(onClick = {
                        viewModel.updateEnv(env.copy(extraEnv = env.extraEnv - key))
                    }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            var newKey by remember { mutableStateOf("") }
            var newValue by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newKey,
                    onValueChange = { newKey = it },
                    label = { Text("KEY") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = newValue,
                    onValueChange = { newValue = it },
                    label = { Text("value") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
                MotionIconButton(
                    onClick = {
                        if (newKey.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) {
                            viewModel.updateEnv(env.copy(extraEnv = env.extraEnv + (newKey to newValue)))
                            newKey = ""
                            newValue = ""
                        }
                    }
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
