package io.github.paulneja.archdroid.ui.desktop

import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
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
import io.github.paulneja.archdroid.ui.components.Disclosure
import io.github.paulneja.archdroid.ui.components.GlassCard
import io.github.paulneja.archdroid.ui.components.LinearProgressIndicator
import io.github.paulneja.archdroid.ui.components.LiquidButton
import io.github.paulneja.archdroid.ui.components.ScreenPage
import io.github.paulneja.archdroid.ui.components.SelectionRow
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

    ScreenPage(stringResource(R.string.nav_desktop), Icons.Rounded.DesktopWindows, onHome = onClose) {
        state.error?.let { ApplyErrorCard(backdrop, it, viewModel::clearError) }

        if (state.running) {
            GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth(), entranceDelay = 0) {
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
            GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth(), entranceDelay = 40) {
                SwitchRow(stringResource(R.string.desktop_extra_keys), extraKeys, backdrop, viewModel::setExtraKeys)
                Hint(stringResource(R.string.desktop_extra_keys_hint))
            }
        }

        if (state.installed.isNotEmpty() && !state.running) {
            GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth(), entranceDelay = 80) {
                Section(stringResource(R.string.desktop_choose))
                state.installed.sortedBy { it.ordinal }.forEach { de ->
                    Choice(de.displayName, choices.desktop == de) { viewModel.selectDesktop(de) }
                }
                Spacer(Modifier.height(8.dp))
                Disclosure(stringResource(R.string.desktop_advanced),
                    "${if (choices.session == SessionType.X11) "X11" else "Wayland"} · ${if (choices.engine == DisplayEngine.X11_NATIVE) "Termux:X11" else "VNC"}") {
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
                }
                Spacer(Modifier.height(12.dp))
                LiquidButton(
                    onClick = { viewModel.start(screenW, screenH) },
                    backdrop = backdrop,
                    enabled = !state.starting && choices.desktop != null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (state.starting) R.string.desktop_starting else R.string.desktop_start),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }

        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth(), entranceDelay = 120) {
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
    SelectionRow(label, selected, enabled = enabled, onClick = onClick)
}

@Composable
private fun Check(label: String, checked: Boolean, onClick: () -> Unit) {
    SelectionRow(label, checked, checkbox = true, onClick = onClick)
}
