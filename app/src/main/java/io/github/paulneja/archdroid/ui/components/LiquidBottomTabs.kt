// Ported from Kyant0/AndroidLiquidGlass (catalog/components/LiquidBottomTabs.kt and
// LiquidBottomTab.kt), Apache License 2.0. The tilt-following highlight mirrors the floating bar
// of the SukiSU-Ultra manager, re-implemented here on top of the backdrop library.

package io.github.paulneja.archdroid.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import io.github.paulneja.archdroid.ui.components.liquid.DampedDragAnimation
import io.github.paulneja.archdroid.ui.components.liquid.InteractiveHighlight
import io.github.paulneja.archdroid.ui.components.liquid.rememberGravityAngle
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

data class LiquidTabItem(val icon: ImageVector, val label: String)

private val LocalLiquidTabScale = staticCompositionLocalOf { { 1f } }

private const val HIGHLIGHT_OFFSET_DEGREES = 135f

@Composable
fun LiquidBottomTabs(
    items: List<LiquidTabItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val tabsCount = items.size
    val accentColor = MaterialTheme.colorScheme.primary
    val contentColor = MaterialTheme.colorScheme.onSurface
    val containerColor = Color(0xFF121212).copy(alpha = 0.4f)
    val tabsBackdrop = rememberLayerBackdrop()
    val onSelectedUpdated by rememberUpdatedState(onSelected)
    val lightAngle by rememberGravityAngle()

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val totalWidth = constraints.maxWidth.toFloat()
        val tabWidth = with(density) { (totalWidth - 8.dp.toPx()) / tabsCount }
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density, totalWidth) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / totalWidth).fastCoerceIn(-1f, 1f)
                with(density) { 4.dp.toPx() } * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }

        var currentIndex by remember { mutableIntStateOf(selectedIndex) }

        fun indexAt(x: Float): Int {
            val logicalX = if (isLtr) x else totalWidth - x
            return ((logicalX - with(density) { 4.dp.toPx() }) / tabWidth).toInt().coerceIn(0, tabsCount - 1)
        }

        val drag = remember(animationScope, tabsCount, tabWidth, isLtr) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = currentIndex.toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = { position -> updateValue(indexAt(position.x).toFloat()) },
                onDragStopped = {
                    val target = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    if (currentIndex != target) {
                        currentIndex = target
                        onSelectedUpdated(target)
                    }
                    animateToValue(target.toFloat())
                    animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDrag = { _, dragAmount ->
                    if (dragAmount.x != 0f) {
                        updateValue(
                            (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                                .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                        )
                        animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                    }
                },
            )
        }

        LaunchedEffect(selectedIndex) {
            if (currentIndex != selectedIndex) {
                currentIndex = selectedIndex
                drag.animateToValue(selectedIndex.toFloat())
            }
        }

        fun activate(index: Int) {
            if (currentIndex != index) {
                currentIndex = index
                onSelectedUpdated(index)
            }
            drag.animateToValue(index.toFloat())
        }

        val interactiveHighlight = remember(animationScope, tabWidth, drag) {
            InteractiveHighlight(animationScope = animationScope) { size, _ ->
                Offset(
                    if (isLtr) (drag.value + 0.5f) * tabWidth + panelOffset
                    else size.width - (drag.value + 0.5f) * tabWidth + panelOffset,
                    size.height / 2f,
                )
            }
        }

        val panelHighlight = Highlight.Default.copy(
            alpha = 0.75f,
            style = HighlightStyle.Default(angle = lightAngle + HIGHLIGHT_OFFSET_DEGREES),
        )
        val pillHighlightStyle = HighlightStyle.Default(angle = lightAngle + HIGHLIGHT_OFFSET_DEGREES + 90f)

        // The accent copy must not be clickable: it sits on top and would swallow drags.
        val tabs: @Composable RowScope.(interactive: Boolean) -> Unit = { interactive ->
            items.forEachIndexed { index, item ->
                LiquidBottomTab(onClick = { activate(index) }, interactive = interactive) {
                    Icon(item.icon, contentDescription = null)
                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }
            }
        }

        // Base glass panel with the real, tappable tabs.
        Row(
            Modifier
                .selectableGroup()
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    highlight = { panelHighlight },
                    shadow = { Shadow(radius = 10.dp, alpha = 0.2f) },
                    layerBlock = {
                        val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, drag.pressProgress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .then(interactiveHighlight.modifier)
                .then(interactiveHighlight.gestureModifier)
                .then(drag.modifier)
                .height(64.dp)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) { tabs(true) }
        }

        CompositionLocalProvider(LocalLiquidTabScale provides { lerp(1f, 1.2f, drag.pressProgress) }) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = drag.pressProgress
                            vibrancy()
                            blur(4.dp.toPx())
                            lens(24.dp.toPx() * progress, 24.dp.toPx() * progress)
                        },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .then(interactiveHighlight.modifier)
                    .height(56.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalContentColor provides accentColor) { tabs(false) }
            }
        }

        // The selection pill.
        Box(
            Modifier
                .clearAndSetSemantics {}
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) drag.value * tabWidth + panelOffset
                        else size.width - (drag.value + 1f) * tabWidth + panelOffset
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = drag.pressProgress
                        lens(
                            10.dp.toPx() * progress,
                            14.dp.toPx() * progress,
                            depthEffect = true,
                            chromaticAberration = true,
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = drag.pressProgress, style = pillHighlightStyle) },
                    shadow = { Shadow(alpha = drag.pressProgress) },
                    innerShadow = { InnerShadow(radius = 8.dp * drag.pressProgress, alpha = drag.pressProgress) },
                    layerBlock = {
                        scaleX = drag.scaleX
                        scaleY = drag.scaleY
                        val velocity = drag.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = drag.pressProgress
                        drawRect(Color.White.copy(0.1f), alpha = 1f - progress)
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    },
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabsCount)
        )
    }
}

@Composable
private fun RowScope.LiquidBottomTab(
    onClick: () -> Unit,
    interactive: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scale = LocalLiquidTabScale.current
    Column(
        Modifier
            .clip(Capsule())
            .then(
                if (interactive) {
                    Modifier.clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
