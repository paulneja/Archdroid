package io.github.paulneja.archdroid.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.Backdrop
import io.github.paulneja.archdroid.R
import io.github.paulneja.archdroid.ui.Routes
import io.github.paulneja.archdroid.ui.components.GlassCard

@Composable
fun SettingsScreen(
    backdrop: Backdrop,
    onOpenSection: (String) -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 64.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            SettingsRow(Icons.Rounded.Folder, stringResource(R.string.settings_mounts)) {
                onOpenSection(Routes.SETTINGS_MOUNTS)
            }
            SettingsRow(Icons.Rounded.Terminal, stringResource(R.string.settings_env)) {
                onOpenSection(Routes.SETTINGS_ENV)
            }
            SettingsRow(Icons.Rounded.Person, stringResource(R.string.settings_user)) {
                onOpenSection(Routes.SETTINGS_USER)
            }
            SettingsRow(Icons.Rounded.PlayCircle, stringResource(R.string.settings_behavior)) {
                onOpenSection(Routes.SETTINGS_BEHAVIOR)
            }
            SettingsRow(Icons.Rounded.Backup, stringResource(R.string.settings_backup)) {
                onOpenSection(Routes.SETTINGS_BACKUP)
            }
            SettingsRow(Icons.Rounded.Info, stringResource(R.string.settings_about)) {
                onOpenSection(Routes.SETTINGS_ABOUT)
            }
        }

        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Rounded.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(horizontal = 6.dp))
                Text(
                    text = stringResource(R.string.settings_dynamic_color),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::updateDynamicColor,
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.padding(horizontal = 6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SettingsSubScreen(
    backdrop: Backdrop,
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 48.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.wizard_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
