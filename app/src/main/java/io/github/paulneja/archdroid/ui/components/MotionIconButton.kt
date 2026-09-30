package io.github.paulneja.archdroid.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.paulneja.archdroid.ui.theme.pressMotion

@Composable
fun MotionIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    IconButton(onClick = onClick, modifier = modifier.pressMotion(interaction), enabled = enabled,
        interactionSource = interaction, content = content)
}
