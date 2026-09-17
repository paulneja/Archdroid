package io.github.paulneja.archdroid.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.termux.view.TerminalView
import io.github.paulneja.archdroid.R

private val TerminalBg = Color(0xFF0A0E14)
private val BarBg = Color(0xFF141A24)
private val KeyBg = Color(0xFF1F2836)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TerminalScreen(
    onClose: () -> Unit,
    viewModel: TerminalViewModel = viewModel(),
) {
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeIndex by viewModel.activeIndex.collectAsStateWithLifecycle()
    val preparing by viewModel.preparing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val broken by viewModel.broken.collectAsStateWithLifecycle()
    val ctrlActive by viewModel.ctrlActive.collectAsStateWithLifecycle()
    val altActive by viewModel.altActive.collectAsStateWithLifecycle()
    val shiftActive by viewModel.shiftActive.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onAllSessionsClosed = { onClose() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(TerminalBg)
            .statusBarsPadding()
            // Order matters: inset modifiers consume what they apply, so the keyboard
            // inset wins while the keyboard is up and the navigation bar inset applies
            // when it is not. Without the second one the extra-keys bar ends up under
            // Android's back/home/recents bar and becomes untappable.
            .imePadding()
            .navigationBarsPadding(),
    ) {
        // Tab bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BarBg)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, tab ->
                    val title by tab.title.collectAsStateWithLifecycle()
                    val selected = index == activeIndex
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) KeyBg else Color.Transparent)
                            .clickable { viewModel.selectTab(index) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                        )
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.terminal_close_tab),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .height(14.dp)
                                .clickable { viewModel.closeTab(index) },
                        )
                    }
                }
            }
            IconButton(onClick = { viewModel.createSession() }) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.terminal_new_tab),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Terminal
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            AndroidView(
                factory = { ctx ->
                    TerminalView(ctx, null).apply {
                        setTextSize(13)
                        setTypeface(android.graphics.Typeface.MONOSPACE)
                        keepScreenOn = true
                        // Without focus the view gets no input connection and the soft
                        // keyboard never appears.
                        isFocusable = true
                        isFocusableInTouchMode = true
                        requestFocus()
                        viewModel.attachView(this)
                    }
                },
                // Without this the ViewModel outlives the view and keeps the Activity alive.
                onRelease = { viewModel.detachView() },
                modifier = Modifier.fillMaxSize(),
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
                    Text(
                        text = stringResource(R.string.terminal_broken_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    broken.forEach { path ->
                        Text(
                            text = path,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
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
        }

        // Extra keys
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
private fun ExtraKey(
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    // A held modifier has to be unmistakable: at a glance you need to know whether the
    // next letter will be Ctrl+C or just a c.
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) MaterialTheme.colorScheme.primary else KeyBg)
            .border(
                width = if (active) 2.dp else 0.dp,
                color = if (active) MaterialTheme.colorScheme.onPrimary else Color.Transparent,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
        )
    }
}
