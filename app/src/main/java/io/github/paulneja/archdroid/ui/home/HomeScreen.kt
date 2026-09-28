package io.github.paulneja.archdroid.ui.home

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.paulneja.archdroid.R
import io.github.paulneja.archdroid.core.chroot.ChrootPaths
import io.github.paulneja.archdroid.core.chroot.ChrootStatus
import io.github.paulneja.archdroid.core.service.InstallState
import io.github.paulneja.archdroid.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    val Accent = MaterialTheme.colorScheme.primary
    val motion = rememberHomeMotion()
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
            ChrootStatus.INSTALLED -> R.string.status_installed
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
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Tune, stringResource(R.string.nav_settings), tint = Muted, modifier = Modifier.size(22.dp))
                }
            }
            AnimatedVisibility(
                visible = entered || !motion,
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 12 },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.fillMaxWidth().height(168.dp).clip(HomeShape).background(Panel)) {
                        OrbitalCore(scene, animate, motion, Modifier.fillMaxSize()) { scroll.value * 0.0004f }
                        Row(Modifier.align(Alignment.TopStart).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(5.dp).background(when (scene) { OrbitalState.DORMANT -> Muted; OrbitalState.ERROR -> MaterialTheme.colorScheme.error; else -> Accent }, RoundedCornerShape(50)))
                            Spacer(Modifier.width(8.dp))
                            AnimatedContent(stateLabel, transitionSpec = {
                                fadeIn(tween(if (motion) 160 else 0)) togetherWith fadeOut(tween(if (motion) 120 else 0))
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
                        HomeAction(stringResource(R.string.nav_terminal),
                            Icons.Rounded.Terminal, onOpenTerminal, primary = true,
                            enabled = status != ChrootStatus.UNKNOWN && !busy)
                        HomeAction(stringResource(R.string.nav_desktop),
                            Icons.Rounded.DesktopWindows, onOpenDesktop)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionHeader(stringResource(R.string.home_system))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric(stringResource(R.string.disk_label), disk, Modifier.weight(1f))
                    Metric(stringResource(R.string.home_mounts), mounts.size.toString(), Modifier.weight(1f))
                }
                Detail(stringResource(R.string.kernel_label), kernel)
                Detail(stringResource(R.string.path_label), ChrootPaths.CHROOT_DIR)
                HorizontalDivider(color = Line.copy(alpha = .5f))
                if (status == ChrootStatus.INSTALLED || status == ChrootStatus.MOUNTED) {
                    Row(
                        Modifier.fillMaxWidth().clip(HomeShape)
                            .clickable(enabled = !busy, onClick = viewModel::toggleMount)
                            .background(Raised).padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (status == ChrootStatus.MOUNTED) Icons.Rounded.PowerSettingsNew else Icons.Rounded.PlayArrow,
                            null, tint = Muted, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(if (status == ChrootStatus.MOUNTED) R.string.unmount_chroot else R.string.mount_chroot),
                            color = if (busy) Muted else Paper, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = Accent, strokeWidth = 1.5.dp)
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
                    AnimatedVisibility(expanded) {
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
private fun SectionHeader(label: String) {
    Text(label, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(HomeShape).background(Panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = Muted, fontSize = 11.sp)
        Text(value, color = Paper, fontSize = 24.sp, fontWeight = FontWeight.Medium,
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

/** All frame state is read in draw: the surrounding menu never recomposes per frame. */
@Composable
private fun OrbitalCore(state: OrbitalState, animated: Boolean, motion: Boolean, modifier: Modifier, parallax: () -> Float) {
    val ready = animateFloatAsState(if (state == OrbitalState.DORMANT) 0f else 1f,
        tween(if (motion) 180 else 0), label = "core unfold")
    val active = animateFloatAsState(if (state == OrbitalState.ACTIVE) 1f else 0f,
        tween(if (motion) 180 else 0), label = "core activity")
    val working = animateFloatAsState(if (state == OrbitalState.WORKING) 1f else 0f,
        tween(if (motion) 180 else 0), label = "core working")
    val failed = animateFloatAsState(if (state == OrbitalState.ERROR) 1f else 0f,
        tween(if (motion) 180 else 0), label = "core error")
    val accent = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val phase = remember { mutableFloatStateOf(.7f) }
    LaunchedEffect(animated, state == OrbitalState.DORMANT) {
        if (!animated || state == OrbitalState.DORMANT) return@LaunchedEffect
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                if (previous != 0L) {
                    val elapsed = ((now - previous) / 1_000_000_000f).coerceAtMost(.05f)
                    val speed = .14f * ready.value + .62f * active.value + .42f * working.value
                    phase.floatValue = (phase.floatValue + elapsed * speed) % (2 * PI).toFloat()
                }
                previous = now
            }
        }
    }
    Canvas(modifier) {
        val t = phase.floatValue + parallax()
        val live = ready.value
        val energy = active.value
        val loading = working.value
        val fault = failed.value
        val radius = size.minDimension * (.27f + .06f * live)
        val center = Offset(size.width * .58f, size.height * .49f)
        val height = .45f + .55f * live
        val wire = androidx.compose.ui.graphics.lerp(Paper, error, fault * .75f)
        val opacity = .16f + .25f * live + .2f * energy
        fun project(latitude: Double, angle: Double): Offset {
            val x = radius * cos(latitude) * cos(angle)
            val z = radius * cos(latitude) * sin(angle)
            val y = radius * sin(latitude) * height
            return center + Offset((x * .85 + y * .32).toFloat(), (y * .82 - x * .3 + z * .35).toFloat())
        }
        for (i in 1..7) {
            val x = size.width * i / 8
            drawLine(Line.copy(alpha = .16f), Offset(x, 0f), Offset(x, size.height), .6.dp.toPx())
        }
        // Collapsed, quiet bands open into the globe when the rootfs is mounted.
        for (ring in 1..9) {
            val latitude = -PI / 2 + PI * ring / 10
            val path = Path()
            for (step in 0..72) {
                val point = project(latitude, step * 2 * PI / 72 + t)
                if (step == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(path, wire.copy(alpha = opacity * (.55f + .35f * ring / 9)), style = Stroke(.7.dp.toPx()))
        }
        for (meridian in 0..11) {
            val angle = meridian * PI / 6 + t
            val path = Path()
            for (step in 0..36) {
                val point = project(-PI / 2 + PI * step / 36, angle)
                if (step == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(path, wire.copy(alpha = (.05f + opacity * live * .65f) * (.7f + .3f * sin(angle).toFloat())), style = Stroke(.7.dp.toPx()))
        }
        // Motion and static orbital markers both distinguish activity with animations disabled.
        val orbitAlpha = .12f * live + .55f * energy + .35f * loading
        for (orbit in 0..1) {
            val path = Path()
            fun orbitPoint(angle: Double): Offset {
                val x = cos(angle).toFloat() * radius * 1.35f
                val y = sin(angle).toFloat() * radius * .38f
                val tilt = if (orbit == 0) -.32f else .5f
                return center + Offset(x, y + x * tilt)
            }
            for (step in 0..96) {
                val point = orbitPoint(step * 2 * PI / 96)
                if (step == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            val orbitColor = androidx.compose.ui.graphics.lerp(wire, accent, energy + loading * .6f)
            drawPath(path, orbitColor.copy(alpha = orbitAlpha * if (orbit == 0) 1f else energy), style = Stroke(.8.dp.toPx()))
            if (energy + loading > .01f) {
                val point = orbitPoint(t.toDouble() * (if (orbit == 0) 1 else -1) + orbit * PI)
                drawCircle(orbitColor.copy(alpha = (energy + loading).coerceAtMost(1f) * .12f), 6.dp.toPx(), point)
                drawCircle(orbitColor.copy(alpha = (energy + loading).coerceAtMost(1f)), 2.dp.toPx(), point)
            }
        }
        if (loading > .01f) {
            drawArc(accent.copy(alpha = loading * .8f), t * 180f / PI.toFloat(), 72f, false,
                center - Offset(radius * 1.12f, radius * 1.12f),
                androidx.compose.ui.geometry.Size(radius * 2.24f, radius * 2.24f), style = Stroke(1.2.dp.toPx()))
        }
    }
}

@Composable
private fun rememberHomeMotion(): Boolean {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = ValueAnimator.areAnimatorsEnabled() }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return enabled
}
