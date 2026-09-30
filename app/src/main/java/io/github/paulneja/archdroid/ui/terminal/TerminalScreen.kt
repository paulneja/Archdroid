package io.github.paulneja.archdroid.ui.terminal

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.termux.view.TerminalView
import io.github.paulneja.archdroid.R
import io.github.paulneja.archdroid.core.terminal.ArchTerminalViewClient
import io.github.paulneja.archdroid.ui.components.CircularProgressIndicator
import io.github.paulneja.archdroid.ui.components.ContainedLoadingIndicator
import io.github.paulneja.archdroid.ui.components.MotionAlertDialog
import io.github.paulneja.archdroid.ui.components.MotionIconButton
import io.github.paulneja.archdroid.ui.theme.AppBackground
import io.github.paulneja.archdroid.ui.theme.AppInput
import io.github.paulneja.archdroid.ui.theme.AppRaised
import io.github.paulneja.archdroid.ui.theme.LocalAppMotion
import io.github.paulneja.archdroid.ui.theme.pressMotion

private val TerminalBg = AppBackground
private val BarBg = AppRaised
private val KeyBg = AppInput

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TerminalScreen(
    visible: Boolean,
    onClose: () -> Unit,
    viewModel: TerminalViewModel = viewModel(),
) {
    val textSize by viewModel.textSize.collectAsStateWithLifecycle()
    val store = viewModel.store
    val tabs by store.tabs.collectAsStateWithLifecycle()
    val activeIndex by store.activeIndex.collectAsStateWithLifecycle()
    val preparing by store.preparing.collectAsStateWithLifecycle()
    val error by store.error.collectAsStateWithLifecycle()
    val closeError by store.closeError.collectAsStateWithLifecycle()
    val pendingProtectedClose by viewModel.pendingProtectedClose.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<Int?>(null) }
    var textMenu by remember { mutableStateOf(false) }
    val broken by store.broken.collectAsStateWithLifecycle()
    val ctrlActive by viewModel.ctrlActive.collectAsStateWithLifecycle()
    val altActive by viewModel.altActive.collectAsStateWithLifecycle()
    val shiftActive by viewModel.shiftActive.collectAsStateWithLifecycle()

    LaunchedEffect(visible) {
        if (visible) viewModel.onShown() else textMenu = false
    }
    LaunchedEffect(Unit) {
        store.allClosed.collect { onClose() }
    }

    if (pendingProtectedClose != null) {
        ProtectedCloseDialog(
            onConfirm = viewModel::confirmProtectedClose,
            onDismiss = viewModel::dismissProtectedClose,
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(TerminalBg)
            .statusBarsPadding()

            .imePadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BarBg)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            MotionIconButton(onClick = onClose) {
                Icon(
                    Icons.Rounded.Home,
                    contentDescription = stringResource(R.string.nav_home),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, tab ->
                    key(tab.id) {
                        val title by tab.title.collectAsStateWithLifecycle()
                        val closing by tab.closing.collectAsStateWithLifecycle()
                        val isProtected by tab.protected.collectAsStateWithLifecycle()
                        val selected = index == activeIndex
                        val interaction = remember { MutableInteractionSource() }
                        val motion = LocalAppMotion.current
                        val selection = animateFloatAsState(if (selected) 1f else 0f, if (motion) tween(220) else snap(), label = "terminal tab")
                        val accent = MaterialTheme.colorScheme.primary
                        Box {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .pressMotion(interaction)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(KeyBg.copy(alpha = selection.value))
                                    .drawBehind {
                                        val width = size.width * selection.value
                                        drawRect(accent.copy(alpha = selection.value * .7f), Offset((size.width - width) / 2, size.height - 2.dp.toPx()), Size(width, 2.dp.toPx()))
                                    }
                                    .semantics { this.selected = selected }
                                    .combinedClickable(
                                        interactionSource = interaction, indication = null, role = Role.Tab,
                                        onClick = { store.selectTab(index) },
                                        onLongClick = { menuFor = index },
                                    )
                                    .heightIn(min = 48.dp).padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (isProtected) {
                                    Icon(
                                        Icons.Rounded.Shield,
                                        contentDescription = stringResource(R.string.terminal_protected),
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier
                                            .padding(end = 4.dp)
                                            .size(14.dp),
                                    )
                                }
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = lerp(MaterialTheme.colorScheme.onSurfaceVariant, accent, selection.value),
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.widthIn(max = 140.dp),
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1,
                                )
                                if (closing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .padding(start = 6.dp)
                                            .size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    MotionIconButton(onClick = { viewModel.requestClose(index) }) {
                                        Icon(Icons.Rounded.Close, stringResource(R.string.terminal_close_tab),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                            DropdownMenu(expanded = menuFor == index, onDismissRequest = { menuFor = null }) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (isProtected) R.string.terminal_unprotect else R.string.terminal_protect
                                            )
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Shield, contentDescription = null) },
                                    onClick = {
                                        store.setProtected(index, !isProtected)
                                        menuFor = null
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.terminal_close_tab)) },
                                    leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null) },
                                    onClick = {
                                        menuFor = null
                                        viewModel.requestClose(index)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Box {
                MotionIconButton(onClick = { textMenu = !textMenu }) {
                    Icon(Icons.Rounded.TextFields, stringResource(R.string.terminal_text_size),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = textMenu, onDismissRequest = { textMenu = false }) {
                    Text(stringResource(R.string.terminal_text_size), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        MotionIconButton(onClick = viewModel::decreaseTextSize,
                            enabled = textSize > ArchTerminalViewClient.MIN_TEXT_SIZE) {
                            Icon(Icons.Rounded.Remove, stringResource(R.string.terminal_smaller_text))
                        }
                        Text(textSize.toString(), fontFamily = FontFamily.Monospace,
                            modifier = Modifier.widthIn(min = 40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        MotionIconButton(onClick = viewModel::increaseTextSize,
                            enabled = textSize < ArchTerminalViewClient.MAX_TEXT_SIZE) {
                            Icon(Icons.Rounded.Add, stringResource(R.string.terminal_larger_text))
                        }
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.terminal_reset_text)) },
                        onClick = { viewModel.resetTextSize(); textMenu = false })
                }
            }
            MotionIconButton(onClick = { store.createSession() }) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.terminal_new_tab),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            AndroidView(
                factory = { ctx ->
                    TerminalView(ctx, null).apply {
                        viewModel.viewClient.applyTextSize(this)
                        setTypeface(android.graphics.Typeface.MONOSPACE)
                        keepScreenOn = true

                        isFocusable = true
                        isFocusableInTouchMode = true
                        requestFocus()
                        viewModel.attachView(this)
                    }
                },

                update = { view ->
                    viewModel.viewClient.applyTextSize(view)
                    viewModel.attachView(view)
                },
                onRelease = { viewModel.detachView() },
                modifier = Modifier.fillMaxSize().clipToBounds(),
            )
            if (preparing) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ContainedLoadingIndicator()
                    Text(
                        text = stringResource(R.string.terminal_preparing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            if (broken.isNotEmpty()) {
                val missingUser = broken.firstOrNull { it.startsWith("user:") }?.removePrefix("user:")
                val missingPaths = broken.filterNot { it.startsWith("user:") }
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                ) {
                    Text(
                        text = stringResource(R.string.terminal_broken_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (missingPaths.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.terminal_broken_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        missingPaths.forEach { path ->
                            Text(
                                text = path,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    } else if (missingUser != null) {
                        Text(
                            text = stringResource(R.string.terminal_missing_user, missingUser),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            error?.let {
                Text(
                    text = stringResource(R.string.terminal_mount_error, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
            }
            closeError?.let {
                Text(
                    text = stringResource(R.string.terminal_close_failed, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BarBg)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ExtraKey("ESC") { viewModel.sendCodePoint(27) }
            ExtraKey("TAB") { viewModel.sendCodePoint(9) }
            ExtraKey("CTRL", active = ctrlActive) { viewModel.toggleCtrl() }
            ExtraKey("ALT", active = altActive) { viewModel.toggleAlt() }
            ExtraKey("SHIFT", active = shiftActive) { viewModel.toggleShift() }
            ExtraKey("KBD") { viewModel.toggleKeyboard() }
            ExtraKey("←") { viewModel.sendText("\u001B[D") }
            ExtraKey("↓") { viewModel.sendText("\u001B[B") }
            ExtraKey("↑") { viewModel.sendText("\u001B[A") }
            ExtraKey("→") { viewModel.sendText("\u001B[C") }
            ExtraKey("HOME") { viewModel.sendText("\u001B[H") }
            ExtraKey("END") { viewModel.sendText("\u001B[F") }
            ExtraKey("PGUP") { viewModel.sendText("\u001B[5~") }
            ExtraKey("PGDN") { viewModel.sendText("\u001B[6~") }
            ExtraKey("|") { viewModel.sendText("|") }
            ExtraKey("/") { viewModel.sendText("/") }
            ExtraKey("~") { viewModel.sendText("~") }
            ExtraKey("-") { viewModel.sendText("-") }
        }
    }
}

@Composable
private fun ProtectedCloseDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    MotionAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Shield, contentDescription = null) },
        title = { Text(stringResource(R.string.terminal_protected_close_title)) },
        text = { Text(stringResource(R.string.terminal_protected_close_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.terminal_close_anyway), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ExtraKey(
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val motion = LocalAppMotion.current
    val selection = animateFloatAsState(if (active) 1f else 0f, if (motion) tween(180) else snap(), label = "modifier key")
    Box(
        modifier = Modifier
            .pressMotion(interaction)
            .clip(RoundedCornerShape(8.dp))
            .background(lerp(KeyBg, MaterialTheme.colorScheme.primary, selection.value))
            .border(
                width = if (active) 2.dp else 0.dp,
                color = if (active) MaterialTheme.colorScheme.onPrimary else Color.Transparent,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            color = lerp(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onPrimary, selection.value),
            fontFamily = FontFamily.Monospace,
        )
    }
}
