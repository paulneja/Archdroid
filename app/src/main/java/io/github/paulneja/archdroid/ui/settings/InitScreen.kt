package io.github.paulneja.archdroid.ui.settings

import androidx.compose.runtime.getValue
import android.app.Application
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.Backdrop
import io.github.paulneja.archdroid.R
import io.github.paulneja.archdroid.core.init.InitSystem
import io.github.paulneja.archdroid.core.init.KernelCapabilities
import io.github.paulneja.archdroid.data.model.AppSettings
import io.github.paulneja.archdroid.data.model.InitMode
import io.github.paulneja.archdroid.di.ServiceLocator
import io.github.paulneja.archdroid.ui.components.GlassCard
import io.github.paulneja.archdroid.ui.components.SelectionRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InitViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ServiceLocator.settings(app)
    private val init = InitSystem(app)

    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _capabilities = MutableStateFlow<KernelCapabilities?>(null)
    val capabilities = _capabilities.asStateFlow()

    fun probe() {
        viewModelScope.launch { _capabilities.value = init.capabilities() }
    }

    fun setMode(mode: InitMode) {
        viewModelScope.launch { repo.updateInitMode(mode) }
    }
}

@Composable
fun InitScreen(
    backdrop: Backdrop,
    onBack: () -> Unit,
    viewModel: InitViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val caps by viewModel.capabilities.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.probe() }
    val systemdOk = caps?.systemdSupported == true

    SettingsSubScreen(backdrop, stringResource(R.string.settings_init), onBack) {
        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            ModeRow(
                title = stringResource(R.string.init_compat),
                body = stringResource(R.string.init_compat_body),
                selected = settings.initMode == InitMode.COMPAT || !systemdOk,
                enabled = true,
            ) { viewModel.setMode(InitMode.COMPAT) }
            Spacer(Modifier.height(8.dp))
            ModeRow(
                title = stringResource(R.string.init_systemd),
                body = stringResource(
                    if (systemdOk) R.string.init_systemd_body else R.string.init_systemd_unsupported
                ),
                selected = settings.initMode == InitMode.SYSTEMD && systemdOk,
                enabled = systemdOk,
            ) { viewModel.setMode(InitMode.SYSTEMD) }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.init_applies_next_start),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.init_kernel_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            val current = caps
            if (current == null) {
                Text(stringResource(R.string.init_probing), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                current.requirements().forEach { req ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        Icon(
                            when (req.present) {
                                true -> Icons.Rounded.CheckCircle
                                false -> Icons.Rounded.Cancel
                                null -> Icons.Rounded.Help
                            },
                            contentDescription = null,
                            tint = when (req.present) {
                                true -> MaterialTheme.colorScheme.primary
                                false -> if (req.requiredForSystemd) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            req.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (req.requiredForSystemd) {
                            Text(
                                stringResource(R.string.init_required),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (!current.configAvailable) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.init_no_config),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeRow(title: String, body: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    SelectionRow(title, selected, description = body, enabled = enabled, onClick = onClick)
}
