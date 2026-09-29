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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import io.github.paulneja.archdroid.data.model.VisualStyle

val LocalPerformanceMode = staticCompositionLocalOf { false }
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

/** No running springs in Material controls when motion is disabled. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal object StillMotion : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}

/** A shared, short settling motion for cards across all pages. */
fun Modifier.panelEntrance() = composed {
    if (!LocalAppMotion.current) return@composed this
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val amount = animateFloatAsState(if (entered) 1f else 0f, tween(320), label = "panel entrance")
    graphicsLayer {
        alpha = amount.value
        translationY = (1f - amount.value) * 14.dp.toPx()
        scaleX = .975f + .025f * amount.value
        scaleY = scaleX
    }
}

fun Modifier.pressMotion(source: MutableInteractionSource) = composed {
    if (!LocalAppMotion.current) return@composed this
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) .975f else 1f, tween(160), label = "press depth")
    graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}
