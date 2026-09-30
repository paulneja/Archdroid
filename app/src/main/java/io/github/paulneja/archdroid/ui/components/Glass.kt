package io.github.paulneja.archdroid.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import io.github.paulneja.archdroid.ui.theme.*
import kotlin.math.roundToInt

private val GlassTint = AppSurface.copy(alpha = 0.78f)

@Composable
fun GlassCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    entranceDelay: Int = 0,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.panelEntrance(entranceDelay)
            .then(if (LocalPerformanceMode.current) Modifier.background(AppSurface, shape) else Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(8f.dp.toPx(), 12f.dp.toPx())
                },
                onDrawSurface = { drawRect(GlassTint) },
            ))
            .padding(16.dp),
        content = { CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content() } },
    )
}

@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier.pressMotion(interaction)
            .then(if (LocalPerformanceMode.current) Modifier.background(if (tint.isSpecified) tint else AppSurface, shape) else Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(4f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(AppSurface)
                    if (tint.isSpecified) drawRect(tint, alpha = if (enabled) 1f else .3f)
                },
            ))
            .clip(shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = { CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content() } },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LiquidToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val accent = MaterialTheme.colorScheme.primary
    if (LocalPerformanceMode.current || !LocalAppMotion.current) {
        Box(modifier.width(56.dp).height(32.dp).clip(Capsule())
            .background(if (checked) accent.copy(alpha = .35f) else AppInput)
            .semantics { toggleableState = if (checked) ToggleableState.On else ToggleableState.Off }
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) },
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.padding(4.dp).width(24.dp).height(24.dp)
                .background(if (checked) accent else AppMuted, Capsule()))
        }
        return
    }
    val trackTint = if (checked) accent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f)
    val offset by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "toggle",
    )

    Box(
        modifier = modifier
            .width(56.dp)
            .height(32.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(4f.dp.toPx())
                    lens(10f.dp.toPx(), 16f.dp.toPx())
                },
                onDrawSurface = { drawRect(trackTint) },
            )
            .clip(Capsule())
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        val travel = with(LocalDensity.current) { 24.dp.toPx() }
        Box(
            modifier = Modifier
                .offset { IntOffset((travel * offset).roundToInt(), 0) }
                .padding(4.dp)
                .width(24.dp)
                .height(24.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        lens(8f.dp.toPx(), 12f.dp.toPx())
                    },
                    onDrawSurface = {
                        drawRect(if (checked) accent else Color.White.copy(alpha = 0.55f))
                    },
                )
        )
    }
}
