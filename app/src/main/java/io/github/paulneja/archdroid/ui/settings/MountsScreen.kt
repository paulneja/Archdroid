package io.github.paulneja.archdroid.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import kotlin.math.roundToInt

@Composable
fun MountsScreen(
    backdrop: Backdrop,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val mounts = settings.mounts

    SettingsSubScreen(backdrop, stringResource(R.string.settings_mounts), onBack) {
        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.mounts_fs_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow("/proc", mounts.proc) { viewModel.updateMounts(mounts.copy(proc = it)) }
            SwitchRow("/sys", mounts.sys) { viewModel.updateMounts(mounts.copy(sys = it)) }
            SwitchRow("/dev", mounts.dev) { viewModel.updateMounts(mounts.copy(dev = it)) }
            SwitchRow("/dev/pts", mounts.devPts) { viewModel.updateMounts(mounts.copy(devPts = it)) }
            SwitchRow("/dev/shm (tmpfs)", mounts.shm) { viewModel.updateMounts(mounts.copy(shm = it)) }
            if (mounts.shm) {
                Text(
                    text = stringResource(R.string.mounts_shm_size, mounts.shmSizeMb),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = mounts.shmSizeMb.toFloat(),
                    onValueChange = {
                        viewModel.updateMounts(mounts.copy(shmSizeMb = it.roundToInt().coerceIn(64, 2048)))
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
            ) { viewModel.updateMounts(mounts.copy(bindSharedStorage = it)) }
            if (mounts.bindSharedStorage) {
                OutlinedTextField(
                    value = mounts.sharedStorageSource,
                    onValueChange = { viewModel.updateMounts(mounts.copy(sharedStorageSource = it)) },
                    label = { Text(stringResource(R.string.mounts_shared_source)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = mounts.sharedStorageTarget,
                    onValueChange = { viewModel.updateMounts(mounts.copy(sharedStorageTarget = it)) },
                    label = { Text(stringResource(R.string.mounts_shared_target)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
                Spacer(Modifier.height(4.dp))
                SwitchRow(
                    stringResource(R.string.mounts_shared_ro),
                    mounts.sharedStorageReadOnly,
                ) { viewModel.updateMounts(mounts.copy(sharedStorageReadOnly = it)) }
            }
        }
    }
}
