package io.github.paulneja.archdroid.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.paulneja.archdroid.R
import io.github.paulneja.archdroid.ui.theme.LocalAppMotion
import io.github.paulneja.archdroid.ui.theme.pressMotion

@Composable
fun Disclosure(title: String, summary: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val motion = LocalAppMotion.current
    val angle = animateFloatAsState(if (expanded) 180f else 0f, if (motion) tween(220) else snap(), label = "disclosure arrow")
    val stateLabel = stringResource(if (expanded) R.string.expanded else R.string.collapsed)
    Column {
        Row(Modifier.fillMaxWidth().pressMotion(source).clip(RoundedCornerShape(8.dp))
            .semantics { stateDescription = stateLabel }.clickable(role = Role.Button, interactionSource = source, indication = null) { expanded = !expanded }
            .heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = angle.value })
        }
        AnimatedVisibility(expanded,
            enter = if (motion) expandVertically(tween(240)) + fadeIn(tween(180)) else EnterTransition.None,
            exit = if (motion) shrinkVertically(tween(200)) + fadeOut(tween(140)) else ExitTransition.None) {
            Column(Modifier.padding(top = 8.dp), content = content)
        }
    }
}
