package io.github.paulneja.archdroid.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.paulneja.archdroid.ui.theme.LocalAppMotion
import io.github.paulneja.archdroid.ui.theme.pressMotion

@Composable
fun SelectionRow(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    checkbox: Boolean = false,
    onClick: () -> Unit,
) {
    val motion = LocalAppMotion.current
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val amount = animateFloatAsState(if (selected) 1f else 0f, if (motion) tween(240) else snap(), label = "selection")
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val click = { if (motion) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
    val input = if (checkbox) Modifier.toggleable(selected, enabled = enabled, role = Role.Checkbox,
        interactionSource = source, indication = null, onValueChange = { click() })
    else Modifier.selectable(selected, enabled = enabled, role = Role.RadioButton,
        interactionSource = source, indication = null, onClick = click)
    Row(modifier.fillMaxWidth().pressMotion(source).clip(RoundedCornerShape(8.dp))
        .background(accent.copy(alpha = .09f * amount.value)).then(input)
        .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(Modifier.size(20.dp)) {
            val p = amount.value.coerceIn(0f, 1f)
            val color = if (enabled) accent else muted
            if (checkbox) {
                drawRoundRect(muted.copy(alpha = 1f - p * .65f), Offset(1.dp.toPx(), 1.dp.toPx()),
                    Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()), CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                drawRoundRect(color.copy(alpha = p), cornerRadius = CornerRadius(3.dp.toPx()))
                if (p > 0f) {
                    val a = Offset(size.width * .22f, size.height * .5f)
                    val b = Offset(size.width * .43f, size.height * .7f)
                    val c = Offset(size.width * .8f, size.height * .29f)
                    val path = Path().apply {
                        moveTo(a.x, a.y)
                        if (p < .4f) { val q = a + (b - a) * (p / .4f); lineTo(q.x, q.y) }
                        else { lineTo(b.x, b.y); val q = b + (c - b) * ((p - .4f) / .6f); lineTo(q.x, q.y) }
                    }
                    drawPath(path, MaterialCheckInk, style = Stroke(2.dp.toPx()))
                }
            } else {
                drawCircle(muted.copy(alpha = 1f - p * .5f), radius = size.width * .43f, style = Stroke(1.5.dp.toPx()))
                drawCircle(color, radius = size.width * .27f * p)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else muted)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
        }
    }
}

private val MaterialCheckInk = io.github.paulneja.archdroid.ui.theme.AppBackground
