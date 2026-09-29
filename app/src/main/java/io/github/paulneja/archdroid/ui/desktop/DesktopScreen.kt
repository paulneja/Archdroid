package io.github.paulneja.archdroid.ui.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import io.github.paulneja.archdroid.ui.components.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.Backdrop
import io.github.paulneja.archdroid.R
import io.github.paulneja.archdroid.core.desktop.DesktopEnvironment
import io.github.paulneja.archdroid.core.desktop.DisplayEngine
import io.github.paulneja.archdroid.core.desktop.SessionType
import io.github.paulneja.archdroid.ui.components.GlassCard
import io.github.paulneja.archdroid.ui.components.LiquidButton
import io.github.paulneja.archdroid.ui.settings.ApplyErrorCard
import io.github.paulneja.archdroid.ui.settings.SwitchRow

@Composable
fun DesktopScreen(
    backdrop: Backdrop,
    onClose: () -> Unit,
    viewModel: DesktopViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val choices by viewModel.choices.collectAsStateWithLifecycle()
    val extraKeys by viewModel.extraKeys.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    val window = LocalWindowInfo.current.containerSize
    val screenW = maxOf(window.width, window.height)
    val screenH = minOf(window.width, window.height)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.nav_desktop),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.nav_home),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClose)
                    .padding(8.dp),
            )
        }

        state.error?.let { ApplyErrorCard(backdrop, it, viewModel::clearError) }

        if (state.running) {
            GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.desktop_running),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                LiquidButton(onClick = viewModel::open, backdrop = backdrop, modifier = Modifier.fillMaxWidth(),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) {
                    Text(stringResource(R.string.desktop_open), style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(8.dp))
                LiquidButton(onClick = viewModel::stop, backdrop = backdrop, modifier = Modifier.fillMaxWidth(),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.25f)) {
                    Text(stringResource(R.string.desktop_stop), style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        if (state.installed.isNotEmpty()) {
            GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
                SwitchRow(stringResource(R.string.desktop_extra_keys), extraKeys, backdrop, viewModel::setExtraKeys)
                Hint(stringResource(R.string.desktop_extra_keys_hint))
            }
        }

        if (state.installed.isNotEmpty() && !state.running) {
            GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
                Section(stringResource(R.string.desktop_choose))
                state.installed.sortedBy { it.ordinal }.forEach { de ->
                    Choice(de.displayName, choices.desktop == de) { viewModel.selectDesktop(de) }
                }
                Spacer(Modifier.height(8.dp))
                Section(stringResource(R.string.desktop_session))
                Choice("X11", choices.session == SessionType.X11) { viewModel.selectSession(SessionType.X11) }
                Choice(
                    stringResource(R.string.desktop_wayland),
                    choices.session == SessionType.WAYLAND,
                    enabled = choices.desktop?.supportsWayland == true,
                ) { viewModel.selectSession(SessionType.WAYLAND) }
                if (choices.desktop?.supportsWayland == false) {
                    Hint(stringResource(R.string.desktop_wayland_unavailable))
                }
                Spacer(Modifier.height(8.dp))
                Section(stringResource(R.string.desktop_engine))
                Choice(stringResource(R.string.desktop_engine_x11), choices.engine == DisplayEngine.X11_NATIVE) {
                    viewModel.selectEngine(DisplayEngine.X11_NATIVE)
                }
                Choice(stringResource(R.string.desktop_engine_vnc), choices.engine == DisplayEngine.VNC) {
                    viewModel.selectEngine(DisplayEngine.VNC)
                }
                Spacer(Modifier.height(12.dp))
                LiquidButton(
                    onClick = { viewModel.start(screenW, screenH) },
                    backdrop = backdrop,
                    enabled = !state.starting && choices.desktop != null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(if (state.starting) R.string.desktop_starting else R.string.desktop_start),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }

        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            Section(
                stringResource(
                    if (state.installed.isEmpty()) R.string.desktop_none_installed else R.string.desktop_install_more
                )
            )
            DesktopEnvironment.entries.filterNot { it in state.installed }.forEach { de ->
                Check(
                    label = "${de.displayName} · ~${de.approxDownloadMb} MB",
                    checked = de in choices.toInstall,
                ) { viewModel.toggleInstall(de) }
            }
            Check(stringResource(R.string.desktop_install_wayland), choices.installWayland) {
                viewModel.setInstallWayland(!choices.installWayland)
            }
            Spacer(Modifier.height(12.dp))
            if (state.installing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.log.takeLast(8).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.heightIn(min = 40.dp),
                )
            } else {
                LiquidButton(
                    onClick = viewModel::install,
                    backdrop = backdrop,
                    enabled = choices.toInstall.any { it !in state.installed },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.desktop_install), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Choice(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Icon(
            if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Check(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Icon(
            if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
