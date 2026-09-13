package io.github.paulneja.archdroid.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.Backdrop
import io.github.paulneja.archdroid.R
import io.github.paulneja.archdroid.core.chroot.ChrootPaths
import io.github.paulneja.archdroid.core.chroot.ChrootStatus
import io.github.paulneja.archdroid.ui.components.GlassCard
import io.github.paulneja.archdroid.ui.components.LiquidButton

@Composable
fun HomeScreen(
    backdrop: Backdrop,
    onOpenTerminal: () -> Unit,
    onInstall: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val kernel by viewModel.kernel.collectAsStateWithLifecycle()
    val diskUsage by viewModel.diskUsage.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val mountError by viewModel.mountError.collectAsStateWithLifecycle()
    val activeMounts by viewModel.activeMounts.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 64.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            InfoRow(
                label = stringResource(R.string.status_label),
                value = when (status) {
                    ChrootStatus.MOUNTED -> stringResource(R.string.status_mounted)
                    ChrootStatus.INSTALLED -> stringResource(R.string.status_installed)
                    ChrootStatus.NOT_INSTALLED -> stringResource(R.string.status_not_installed)
                    ChrootStatus.UNKNOWN -> "…"
                },
                valueColor = when (status) {
                    ChrootStatus.MOUNTED -> MaterialTheme.colorScheme.primary
                    ChrootStatus.INSTALLED -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            InfoRow(stringResource(R.string.distro_label), "Arch Linux ARM (aarch64)")
            InfoRow(
                stringResource(R.string.path_label),
                ChrootPaths.CHROOT_DIR,
                monospace = true,
            )
            InfoRow(stringResource(R.string.kernel_label), kernel, monospace = true)
            InfoRow(stringResource(R.string.disk_label), diskUsage, monospace = true)
        }

        if (activeMounts.isNotEmpty()) {
            GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.mounts_live_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    text = stringResource(R.string.mounts_live_body, ChrootPaths.CHROOT_DIR),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                activeMounts.forEach { point ->
                    Text(
                        text = point,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        mountError?.let { message ->
            GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.mount_failed),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when (status) {
            ChrootStatus.NOT_INSTALLED -> {
                GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.not_installed_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.not_installed_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LiquidButton(
                    onClick = onInstall,
                    backdrop = backdrop,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.install_arch),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            else -> {
                LiquidButton(
                    onClick = onOpenTerminal,
                    backdrop = backdrop,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Terminal, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.open_terminal),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                LiquidButton(
                    onClick = viewModel::toggleMount,
                    backdrop = backdrop,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = if (status == ChrootStatus.MOUNTED)
                            stringResource(R.string.unmount_chroot)
                        else
                            stringResource(R.string.mount_chroot),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    monospace: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontFamily = if (monospace) FontFamily.Monospace else null,
        )
    }
}
