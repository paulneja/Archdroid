package io.github.paulneja.archdroid.ui.install

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.kyant.backdrop.Backdrop
import io.github.paulneja.archdroid.R
import io.github.paulneja.archdroid.core.chroot.ChrootInstaller
import io.github.paulneja.archdroid.core.service.InstallState
import io.github.paulneja.archdroid.data.model.RootfsDownloadMode
import io.github.paulneja.archdroid.ui.components.GlassCard
import io.github.paulneja.archdroid.ui.components.LiquidButton
import io.github.paulneja.archdroid.ui.components.LiquidToggle
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InstallWizardScreen(
    backdrop: Backdrop,
    onDone: () -> Unit,
    viewModel: InstallWizardViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    fun notificationsDeniedNow(): Boolean = NotificationPermissionStatus.denied(
        apiLevel = Build.VERSION.SDK_INT,
        permissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED,
    )
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.setNotificationsDenied(notificationsDeniedNow())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.setNotificationsDenied(notificationsDeniedNow())
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setNotificationsDenied(!granted)
        viewModel.install()
    }
    val requestPermissionAndInstall = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setNotificationsDenied(false)
            viewModel.install()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 48.dp, bottom = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.wizard_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.wizard_step, (ui.page + 1).coerceAtMost(5), 5),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Box(Modifier.weight(1f)) {
            AnimatedContent(targetState = ui.page, label = "wizard") { page ->
                when (page) {
                    0 -> WelcomePage(ui)
                    1 -> MirrorPage(ui, viewModel)
                    2 -> PackagesPage(ui, viewModel)
                    3 -> UserPage(backdrop, ui, viewModel)
                    4 -> ProgressPage(backdrop, ui)
                    else -> DonePage(backdrop, ui)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            ui.done -> LiquidButton(
                onClick = {
                    viewModel.acknowledgeTerminalState()
                    onDone()
                },
                backdrop = backdrop,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.wizard_finish), style = MaterialTheme.typography.titleSmall)
            }
            ui.installState == InstallState.Queued || ui.installState is InstallState.Running ->
                InstallActionButtons(
                    backdrop = backdrop,
                    primaryLabel = stringResource(R.string.wizard_pause),
                    onPrimary = viewModel::pause,
                    onCancel = viewModel::cancel,
                )
            ui.installState is InstallState.Stopping -> {}
            ui.installState is InstallState.Paused -> InstallActionButtons(
                backdrop = backdrop,
                primaryLabel = stringResource(R.string.wizard_resume),
                onPrimary = viewModel::resume,
                onCancel = viewModel::cancel,
            )
            ui.installState is InstallState.Failed -> LiquidButton(
                onClick = viewModel::retry,
                backdrop = backdrop,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.wizard_retry_install),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            ui.installState == InstallState.Cancelled -> LiquidButton(
                onClick = viewModel::retry,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.wizard_retry_install),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            else -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (ui.page > 0) {
                    LiquidButton(
                        onClick = viewModel::prevPage,
                        backdrop = backdrop,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.wizard_back), style = MaterialTheme.typography.titleSmall)
                    }
                }
                val isLast = ui.page == 3
                LiquidButton(
                    onClick = { if (isLast) requestPermissionAndInstall() else viewModel.nextPage() },
                    backdrop = backdrop,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    enabled = !isLast || viewModel.canInstall(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(if (isLast) R.string.wizard_install else R.string.wizard_next),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun InstallActionButtons(
    backdrop: Backdrop,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LiquidButton(
            onClick = onPrimary,
            backdrop = backdrop,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            modifier = Modifier.weight(1f),
        ) {
            Text(primaryLabel, style = MaterialTheme.typography.titleSmall)
        }
        LiquidButton(
            onClick = onCancel,
            backdrop = backdrop,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                stringResource(R.string.wizard_cancel_install),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
private fun WelcomePage(ui: InstallWizardViewModel.UiState) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = stringResource(R.string.wizard_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        RequirementRow(stringResource(R.string.wizard_req_root), ok = true)
        RequirementRow(
            stringResource(
                R.string.wizard_req_space,
                formatBytes(ui.freeBytes),
            ),
            ok = ui.freeBytes >= InstallWizardViewModel.MIN_FREE_BYTES,
        )
        RequirementRow(
            stringResource(R.string.wizard_req_kernel, ui.kernel),
            ok = true,
        )
        RequirementRow(stringResource(R.string.wizard_req_network), ok = true)
    }
}

@Composable
private fun RequirementRow(text: String, ok: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        Icon(
            imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun MirrorPage(ui: InstallWizardViewModel.UiState, viewModel: InstallWizardViewModel) {
    Column {
        Text(
            text = stringResource(R.string.wizard_mirror_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        FilterChip(
            selected = ui.downloadMode == RootfsDownloadMode.FAST,
            onClick = { viewModel.setDownloadMode(RootfsDownloadMode.FAST) },
            label = {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(stringResource(R.string.wizard_download_fast))
                    Text(
                        stringResource(R.string.wizard_download_fast_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        FilterChip(
            selected = ui.downloadMode == RootfsDownloadMode.SECURE,
            onClick = { viewModel.setDownloadMode(RootfsDownloadMode.SECURE) },
            label = {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(stringResource(R.string.wizard_download_secure))
                    Text(
                        stringResource(R.string.wizard_download_secure_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PackagesPage(ui: InstallWizardViewModel.UiState, viewModel: InstallWizardViewModel) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = stringResource(R.string.wizard_packages_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InstallWizardViewModel.AVAILABLE_PACKAGES.forEach { pkg ->
                FilterChip(
                    selected = pkg in ui.selectedPackages,
                    onClick = { viewModel.togglePackage(pkg) },
                    label = { Text(pkg, fontFamily = FontFamily.Monospace) },
                    leadingIcon = if (pkg in ui.selectedPackages) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun UserPage(
    backdrop: Backdrop,
    ui: InstallWizardViewModel.UiState,
    viewModel: InstallWizardViewModel,
) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.wizard_login_as_root),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            LiquidToggle(
                checked = ui.loginAsRoot,
                onCheckedChange = viewModel::setLoginAsRoot,
                backdrop = backdrop,
            )
        }
        if (!ui.loginAsRoot) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = ui.username,
                onValueChange = viewModel::setUsername,
                label = { Text(stringResource(R.string.wizard_username)) },
                singleLine = true,
                isError = !viewModel.usernameValid(),
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ui.password,
                onValueChange = viewModel::setPassword,
                label = { Text(stringResource(R.string.wizard_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.wizard_user_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProgressPage(backdrop: Backdrop, ui: InstallWizardViewModel.UiState) {
    Column {
        val step = ui.progress?.step
        Text(
            text = step?.let { stepLabel(it) } ?: stringResource(R.string.step_download),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        val p = ui.progress
        if (p != null && p.step == ChrootInstaller.Step.DOWNLOAD && p.totalBytes > 0) {
            val fraction = p.downloadedBytes.toFloat() / p.totalBytes
            LinearWavyProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.downloaded_progress,
                    formatBytes(p.downloadedBytes),
                    formatBytes(p.totalBytes),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(16.dp))
        if (ui.notificationsDenied) {
            Text(
                text = stringResource(R.string.wizard_notifications_denied),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(12.dp))
        }
        if (ui.installState == InstallState.Cancelled) {
            Text(
                text = stringResource(R.string.wizard_cancelled),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
        GlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .height(220.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                ui.log.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ui.error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun DonePage(backdrop: Backdrop, ui: InstallWizardViewModel.UiState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(32.dp))
        Icon(
            imageVector = if (ui.error == null) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
            contentDescription = null,
            tint = if (ui.error == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(if (ui.error == null) R.string.wizard_done_title else R.string.wizard_failed_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = ui.error ?: stringResource(R.string.wizard_done_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun stepLabel(step: ChrootInstaller.Step): String = stringResource(
    when (step) {
        ChrootInstaller.Step.DOWNLOAD -> R.string.step_download
        ChrootInstaller.Step.VERIFY -> R.string.step_verify
        ChrootInstaller.Step.EXTRACT -> R.string.step_extract
        ChrootInstaller.Step.BASE_CONFIG -> R.string.step_base_config
        ChrootInstaller.Step.USER -> R.string.step_user
        ChrootInstaller.Step.PACKAGES -> R.string.step_packages
        ChrootInstaller.Step.CLEANUP -> R.string.step_cleanup
    }
)

private fun formatBytes(bytes: Long): String = when {
    bytes < 0 -> "—"
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.0f MB", bytes / (1L shl 20).toDouble())
    else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
}
