package io.github.paulneja.archdroid.ui.theme

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.paulneja.archdroid.data.model.VisualStyle

val LocalPerformanceMode = staticCompositionLocalOf { false }
val LocalPageVisible = staticCompositionLocalOf { true }
val LocalAppMotion = staticCompositionLocalOf { true }
val LocalVisualStyle = staticCompositionLocalOf { VisualStyle.CHROME }

@Composable
internal fun rememberSystemMotion(): Boolean {
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal object StillMotion : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}

fun Modifier.panelEntrance(delayMillis: Int = 0) = composed {
    if (!LocalAppMotion.current) return@composed this
    var entered by remember { mutableStateOf(false) }
    val visible = LocalPageVisible.current
    LaunchedEffect(visible) { entered = visible }
    val amount = animateFloatAsState(if (entered) 1f else 0f, tween(280, delayMillis = if (entered) delayMillis else 0), label = "panel entrance")
    graphicsLayer {
        alpha = .35f + .65f * amount.value
        translationY = (1f - amount.value) * 14.dp.toPx()
        scaleX = .975f + .025f * amount.value
        scaleY = scaleX
    }
}

fun Modifier.pressMotion(source: MutableInteractionSource) = composed {
    if (!LocalAppMotion.current) return@composed this
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) .975f else 1f, tween(160), label = "press depth")
    val glow = animateFloatAsState(if (pressed) .14f else 0f, tween(200), label = "touch light")
    graphicsLayer { scaleX = scale.value; scaleY = scale.value }.drawWithContent {
        drawContent()
        drawRoundRect(Color.White.copy(alpha = glow.value), cornerRadius = CornerRadius(8.dp.toPx()))
    }
}
