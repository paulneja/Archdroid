package io.github.paulneja.archdroid.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.paulneja.archdroid.R
import io.github.paulneja.archdroid.core.chroot.ChrootPaths
import io.github.paulneja.archdroid.core.chroot.ChrootStatus
import io.github.paulneja.archdroid.core.service.InstallState
import io.github.paulneja.archdroid.ui.components.CircularProgressIndicator
import io.github.paulneja.archdroid.ui.components.MotionAlertDialog
import io.github.paulneja.archdroid.ui.components.MotionIconButton
import io.github.paulneja.archdroid.ui.theme.*

private val Ink = AppBackground
private val Panel = AppSurface
private val Raised = AppRaised
private val Line = AppBorder
private val Paper = AppText
private val Muted = AppMuted
private val HomeShape = RoundedCornerShape(10.dp)

@Composable
fun HomeScreen(
    onOpenTerminal: () -> Unit,
    onOpenDesktop: () -> Unit,
    onOpenSettings: () -> Unit,
    onInstall: () -> Unit,
    visible: Boolean,
    viewModel: HomeViewModel = viewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val kernel by viewModel.kernel.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val disk by viewModel.diskUsage.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.mountError.collectAsStateWithLifecycle()
    val mounts by viewModel.activeMounts.collectAsStateWithLifecycle()
    val desktop by viewModel.desktopState.collectAsStateWithLifecycle()
    val preparing by viewModel.preparing.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val install by viewModel.installState.collectAsStateWithLifecycle()
    val terminalWorking by viewModel.terminalWorking.collectAsStateWithLifecycle()
    val scene = orbitalState(
        status,
        busy || preparing || desktop.starting || desktop.installing ||
            install is InstallState.Running || install is InstallState.Stopping,
        error != null,
        desktop.running || (tabs.isNotEmpty() && terminalWorking),
    )
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    var confirmUnmount by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val requestMount: () -> Unit = {
        if (status == ChrootStatus.MOUNTED && (tabs.isNotEmpty() || desktop.running)) confirmUnmount = true
        else viewModel.toggleMount()
    }
    if (confirmUnmount) {
        MotionAlertDialog(onDismissRequest = { confirmUnmount = false },
            title = { Text(stringResource(R.string.home_unmount_title)) },
            text = { Text(stringResource(R.string.home_unmount_body)) },
            confirmButton = { TextButton(onClick = { confirmUnmount = false; viewModel.toggleMount() }) {
                Text(stringResource(R.string.unmount_chroot))
            } },
            dismissButton = { TextButton(onClick = { confirmUnmount = false }) { Text(stringResource(R.string.cancel)) } })
    }
    val Accent = MaterialTheme.colorScheme.primary
    val motion = LocalAppMotion.current
    val performance = LocalPerformanceMode.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val animate = motion && visible && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(visible, lifecycle) {
        if (visible && lifecycle.isAtLeast(Lifecycle.State.RESUMED)) viewModel.watchActivity()
    }
    var entered by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    LaunchedEffect(visible) {
        entered = visible
        if (visible) viewModel.refresh()
    }
    val stateLabel = stringResource(when (scene) {
        OrbitalState.ACTIVE -> R.string.home_active
        OrbitalState.WORKING -> R.string.home_working
        OrbitalState.ERROR -> R.string.mount_failed
        else -> when (status) {
            ChrootStatus.MOUNTED -> R.string.status_mounted
            ChrootStatus.INSTALLED -> R.string.home_unmounted
            ChrootStatus.NOT_INSTALLED -> R.string.status_not_installed
            ChrootStatus.UNKNOWN -> R.string.home_checking
        }
    })

    Box(Modifier.fillMaxSize().background(Ink)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(scroll)
                .padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ArchMark(Modifier.size(25.dp))
                Spacer(Modifier.width(10.dp))
                Text("ARCHDROID", color = Paper, fontSize = 14.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                MotionIconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Tune, stringResource(R.string.nav_settings), tint = Muted, modifier = Modifier.size(22.dp))
                }
            }
            AnimatedVisibility(
                visible = entered || !motion,
                enter = fadeIn(tween(if (motion) 260 else 0)) + slideInVertically(tween(if (motion) 320 else 0)) { it / 12 },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.fillMaxWidth().height(if (performance) 92.dp else 220.dp).clip(HomeShape).background(Panel)) {
                        OrbitalCore(scene, animate, motion, Modifier.fillMaxSize()) { scroll.value * 0.0004f }
                        Row(Modifier.align(Alignment.TopStart).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(5.dp).background(when (scene) { OrbitalState.DORMANT -> Muted; OrbitalState.ERROR -> MaterialTheme.colorScheme.error; else -> Accent }, RoundedCornerShape(50)))
                            Spacer(Modifier.width(8.dp))
                            AnimatedContent(stateLabel, transitionSpec = {
                                (fadeIn(tween(if (motion) 160 else 0)) togetherWith fadeOut(tween(if (motion) 120 else 0))).using(null)
                            }, label = "system state") { label ->
                                Text(label, color = Paper, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                            Text("Arch Linux ARM", color = Paper, fontSize = 12.sp)
                        }
                    }
                    if (status == ChrootStatus.NOT_INSTALLED) {
                        Text(stringResource(R.string.not_installed_body), color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                        HomeAction(stringResource(R.string.install_arch),
                            Icons.Rounded.Download, onInstall, primary = true)
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            HomeLaunchTile(stringResource(R.string.nav_terminal), Icons.Rounded.Terminal,
                                onOpenTerminal, Modifier.weight(1f),
                                active = tabs.isNotEmpty(), enabled = status != ChrootStatus.UNKNOWN && !busy,
                                actionIcon = Icons.Rounded.Add, actionLabel = stringResource(R.string.terminal_new_tab),
                                onAction = { viewModel.newTerminal(); onOpenTerminal() })
                            HomeLaunchTile(stringResource(R.string.nav_desktop), Icons.Rounded.DesktopWindows,
                                { if (desktop.running) viewModel.resumeDesktop() else onOpenDesktop() }, Modifier.weight(1f),
                                active = desktop.running, enabled = !desktop.starting && !busy,
                                actionIcon = Icons.Rounded.Tune, actionLabel = stringResource(R.string.home_desktop_options),
                                onAction = onOpenDesktop)
                        }
                    }
                }
            }
            if (status == ChrootStatus.INSTALLED || status == ChrootStatus.MOUNTED) {
                Row(Modifier.fillMaxWidth().clip(HomeShape).background(Raised)
                    .clickable(enabled = !busy && !preparing && !desktop.starting && !desktop.installing, onClick = requestMount)
                    .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (status == ChrootStatus.MOUNTED) Icons.Rounded.PowerSettingsNew else Icons.Rounded.PlayArrow,
                        null, tint = Muted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(if (status == ChrootStatus.MOUNTED) R.string.unmount_chroot else R.string.mount_chroot),
                        color = if (busy) Muted else Paper, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = Accent, strokeWidth = 1.5.dp)
                }
            }
            Column(Modifier.fillMaxWidth().clip(HomeShape).background(Panel).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_system), color = Paper, fontSize = 14.sp,
                        fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    MotionIconButton(onClick = viewModel::refresh, enabled = !refreshing, modifier = Modifier.size(48.dp)) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp, color = Muted)
                        else Icon(Icons.Rounded.Refresh, stringResource(R.string.mounts_refresh), tint = Muted, modifier = Modifier.size(20.dp))
                    }
                    MotionIconButton(onClick = { detailsExpanded = !detailsExpanded }, modifier = Modifier.size(48.dp)) {
                        Icon(if (detailsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            stringResource(R.string.home_system_details), tint = Muted, modifier = Modifier.size(20.dp))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric(stringResource(R.string.disk_label), disk, Modifier.weight(1f))
                    Metric(stringResource(R.string.home_mounts), mounts.size.toString(), Modifier.weight(1f))
                }
                AnimatedVisibility(detailsExpanded, enter = fadeIn(tween(if (motion) 160 else 0)), exit = fadeOut(tween(if (motion) 120 else 0))) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalDivider(color = Line.copy(alpha = .5f))
                        Detail(stringResource(R.string.kernel_label), kernel)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { Detail(stringResource(R.string.path_label), ChrootPaths.CHROOT_DIR) }
                            MotionIconButton(onClick = { clipboard.setText(AnnotatedString(ChrootPaths.CHROOT_DIR)) }) {
                                Icon(Icons.Rounded.ContentCopy, stringResource(R.string.home_copy_path), tint = Muted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            error?.let {
                Column(Modifier.fillMaxWidth().clip(HomeShape).background(Raised).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.mount_failed), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    Text(it, color = Paper, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    TextButton(onClick = viewModel::clearMountError) { Text(stringResource(R.string.home_dismiss), color = Paper) }
                }
            }
            if (mounts.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth().clip(HomeShape).clickable { expanded = !expanded }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.mounts_live_title), color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Muted, modifier = Modifier.size(20.dp))
                    }
                    AnimatedVisibility(expanded, enter = fadeIn(tween(if (motion) 180 else 0)), exit = fadeOut(tween(if (motion) 120 else 0))) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.mounts_live_body, ChrootPaths.CHROOT_DIR), color = Muted, fontSize = 12.sp)
                            mounts.forEach { Text(it, color = Paper, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeAction(title: String, icon: ImageVector, onClick: () -> Unit,
                       primary: Boolean = false, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().clip(HomeShape).background(if (primary) MaterialTheme.colorScheme.primary else Raised)
        .clickable(enabled = enabled, onClick = onClick).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        val foreground = if (primary) Ink else Paper
        Icon(icon, null, tint = foreground.copy(alpha = if (enabled) 1f else .4f), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, color = foreground.copy(alpha = if (enabled) 1f else .4f), fontSize = 16.sp,
            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.ArrowOutward, null, tint = foreground, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun HomeLaunchTile(title: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier,
                           active: Boolean, enabled: Boolean, actionIcon: ImageVector,
                           actionLabel: String, onAction: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(modifier.panelEntrance().pressMotion(interaction).clip(HomeShape).background(Raised).clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (active) MaterialTheme.colorScheme.primary else Muted,
                modifier = Modifier.size(24.dp))
            Spacer(Modifier.weight(1f))
            MotionIconButton(onClick = onAction, enabled = enabled) {
                Icon(actionIcon, actionLabel, tint = Muted, modifier = Modifier.size(20.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = if (enabled) Paper else Muted, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f))
            if (active) Icon(Icons.Rounded.ArrowOutward, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Muted, fontSize = 11.sp)
        Text(value, color = Paper, fontSize = 20.sp, fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(value, color = Paper, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun ArchMark(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * .5f, 0f); lineTo(w, h); lineTo(w * .66f, h * .86f)
            quadraticTo(w * .64f, h * .59f, w * .5f, h * .59f)
            quadraticTo(w * .36f, h * .59f, w * .34f, h * .86f)
            lineTo(0f, h); close()
        }
        drawPath(path, Paper)
    }
}
