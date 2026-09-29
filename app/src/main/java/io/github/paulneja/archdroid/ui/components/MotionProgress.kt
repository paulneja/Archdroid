package io.github.paulneja.archdroid.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.paulneja.archdroid.ui.theme.LocalAppMotion

@Composable
fun CircularProgressIndicator(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, strokeWidth: Dp = 4.dp) {
    if (LocalAppMotion.current) androidx.compose.material3.CircularProgressIndicator(modifier = modifier, color = color, strokeWidth = strokeWidth)
    else Canvas(modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
        val width = strokeWidth.toPx()
        drawArc(color, -90f, 260f, false, Offset(width / 2, width / 2),
            Size((size.width - width).coerceAtLeast(0f), (size.height - width).coerceAtLeast(0f)), style = Stroke(width))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LinearWavyProgressIndicator(progress: (() -> Float)? = null, modifier: Modifier = Modifier) {
    if (LocalAppMotion.current) {
        if (progress == null) androidx.compose.material3.LinearWavyProgressIndicator(modifier = modifier)
        else androidx.compose.material3.LinearWavyProgressIndicator(progress = progress, modifier = modifier)
    } else StillProgress(progress, modifier)
}

@Composable
fun LinearProgressIndicator(modifier: Modifier = Modifier) {
    if (LocalAppMotion.current) androidx.compose.material3.LinearProgressIndicator(modifier = modifier)
    else StillProgress(null, modifier)
}

@Composable
private fun StillProgress(progress: (() -> Float)?, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val value = progress?.invoke()?.coerceIn(0f, 1f)
    Canvas(modifier.height(4.dp).semantics {
        progressBarRangeInfo = if (value == null) ProgressBarRangeInfo.Indeterminate else ProgressBarRangeInfo(value, 0f..1f)
    }) {
        drawRect(accent.copy(alpha = .15f))
        if (value != null) drawRect(accent, size = Size(size.width * value, size.height))
        else drawRect(accent, topLeft = Offset(size.width * .35f, 0f), size = Size(size.width * .3f, size.height))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContainedLoadingIndicator(modifier: Modifier = Modifier) {
    if (LocalAppMotion.current) androidx.compose.material3.ContainedLoadingIndicator(modifier = modifier)
    else CircularProgressIndicator(modifier = modifier.then(Modifier.size(48.dp)))
}
