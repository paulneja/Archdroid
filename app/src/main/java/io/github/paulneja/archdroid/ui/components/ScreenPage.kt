package io.github.paulneja.archdroid.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.paulneja.archdroid.R
import io.github.paulneja.archdroid.ui.theme.LocalPerformanceMode
import io.github.paulneja.archdroid.ui.theme.panelEntrance

@Composable
fun ScreenPage(title: String, icon: ImageVector, onHome: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    val accent = MaterialTheme.colorScheme.primary
    val performance = LocalPerformanceMode.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().panelEntrance().padding(start = 24.dp, end = 12.dp).height(64.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            if (onHome != null) MotionIconButton(onClick = onHome) {
                Icon(Icons.Rounded.Home, stringResource(R.string.nav_home), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
            if (!performance) Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                if (scroll.maxValue > 0 && scroll.value > 0) {
                    val fraction = scroll.value.toFloat() / scroll.maxValue
                    drawRect(accent.copy(alpha = .5f), size = size.copy(width = size.width * fraction))
                }
            }
        }
    }
}
