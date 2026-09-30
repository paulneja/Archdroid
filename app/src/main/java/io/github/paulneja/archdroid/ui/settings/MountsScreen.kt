package io.github.paulneja.archdroid.ui.settings

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
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
import io.github.paulneja.archdroid.core.chroot.MountPlan
import io.github.paulneja.archdroid.data.model.DevMode
import io.github.paulneja.archdroid.ui.components.GlassCard
import io.github.paulneja.archdroid.ui.components.MotionIconButton
import kotlin.math.roundToInt

@Composable
fun MountsScreen(
    backdrop: Backdrop,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val mounts = settings.mounts
    val active by viewModel.activeMounts.collectAsStateWithLifecycle()
    val applyError by viewModel.applyError.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshMounts() }

    SettingsSubScreen(backdrop, stringResource(R.string.settings_mounts), onBack) {
        applyError?.let { ApplyErrorCard(backdrop, it, viewModel::clearApplyError) }
        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.mounts_active_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                MotionIconButton(onClick = { viewModel.refreshMounts() }) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.mounts_refresh),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            if (active.isEmpty()) {
                Text(
                    stringResource(R.string.mounts_active_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                active.forEach { point ->
                    Text(
                        point,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.mounts_fs_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow("/proc", mounts.proc, backdrop) { viewModel.updateMounts(mounts.copy(proc = it)) }
            SwitchRow("/sys", mounts.sys, backdrop) { viewModel.updateMounts(mounts.copy(sys = it)) }
            SwitchRow("/dev", mounts.dev, backdrop) { viewModel.updateMounts(mounts.copy(dev = it)) }
            if (mounts.dev) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.mounts_dev_mode_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchRow(
                    stringResource(R.string.mounts_dev_use_android),
                    mounts.devMode == DevMode.BIND,
                    backdrop,
                ) { useAndroid ->
                    viewModel.updateMounts(
                        mounts.copy(devMode = if (useAndroid) DevMode.BIND else DevMode.PRIVATE),
                    )
                }
                Text(
                    text = stringResource(
                        if (mounts.devMode == DevMode.BIND) R.string.mounts_dev_bind_hint
                        else R.string.mounts_dev_private_hint,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (mounts.devMode == DevMode.BIND) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }
            SwitchRow("/dev/pts", mounts.devPts, backdrop) { viewModel.updateMounts(mounts.copy(devPts = it)) }
            SwitchRow("/dev/shm (tmpfs)", mounts.shm, backdrop) { viewModel.updateMounts(mounts.copy(shm = it)) }
            if (mounts.shm) {
                var shmDraft by remember(mounts.shmSizeMb) { mutableFloatStateOf(mounts.shmSizeMb.toFloat()) }
                Text(
                    text = stringResource(R.string.mounts_shm_size, shmDraft.roundToInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = shmDraft,
                    onValueChange = { shmDraft = it },
                    onValueChangeFinished = {
                        viewModel.updateMounts(mounts.copy(shmSizeMb = shmDraft.roundToInt().coerceIn(64, 2048)))
                    },
                    valueRange = 64f..2048f,
                )
            }
        }

        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.mounts_shared_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                stringResource(R.string.mounts_shared_enable),
                mounts.bindSharedStorage,
                backdrop,
            ) { viewModel.updateMounts(mounts.copy(bindSharedStorage = it)) }
            if (mounts.bindSharedStorage) {
                DraftField(
                    value = mounts.sharedStorageSource,
                    label = stringResource(R.string.mounts_shared_source),
                    onCommit = { viewModel.updateMounts(mounts.copy(sharedStorageSource = it)) },
                    isValid = { MountPlan.validate(mounts.copy(bindSharedStorage = true, sharedStorageSource = it)).isEmpty() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                DraftField(
                    value = mounts.sharedStorageTarget,
                    label = stringResource(R.string.mounts_shared_target),
                    onCommit = { viewModel.updateMounts(mounts.copy(sharedStorageTarget = it)) },
                    isValid = { MountPlan.validate(mounts.copy(bindSharedStorage = true, sharedStorageTarget = it)).isEmpty() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                SwitchRow(
                    stringResource(R.string.mounts_shared_ro),
                    mounts.sharedStorageReadOnly,
                    backdrop,
                ) { viewModel.updateMounts(mounts.copy(sharedStorageReadOnly = it)) }
            }
        }
    }
}
