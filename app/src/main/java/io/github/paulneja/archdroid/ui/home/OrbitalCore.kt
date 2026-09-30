package io.github.paulneja.archdroid.ui.home

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.core.spring
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.paulneja.archdroid.data.model.VisualStyle
import io.github.paulneja.archdroid.ui.theme.LocalVisualStyle
import io.github.paulneja.archdroid.ui.theme.LocalPerformanceMode
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.paulneja.archdroid.ui.theme.AppText
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun OrbitalCore(
    state: OrbitalState,
    animated: Boolean,
    motion: Boolean,
    modifier: Modifier,
    parallax: () -> Float,
) {
    if (LocalPerformanceMode.current) {
        val color = if (state == OrbitalState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        Canvas(modifier) {

            val center = Offset(size.width * .83f, size.height * .5f)
            drawCircle(color.copy(alpha = .14f), 23.dp.toPx(), center)
            drawCircle(color, 6.dp.toPx(), center)
        }
        return
    }
    val style = LocalVisualStyle.current
    val material = animateFloatAsState(style.ordinal.toFloat(), tween(if (motion) 420 else 0), label = "orb material")
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val tiltX = animateFloatAsState(dragX, if (motion) spring(.85f, 180f) else tween(0), label = "orb tilt x")
    val tiltY = animateFloatAsState(dragY, if (motion) spring(.85f, 180f) else tween(0), label = "orb tilt y")
    val gestures = if (animated) Modifier.pointerInput(Unit) {
        detectDragGestures(onDragEnd = { dragX = 0f; dragY = 0f }, onDragCancel = { dragX = 0f; dragY = 0f }) { change, delta ->
            change.consume()
            dragX = (dragX + delta.x / size.width).coerceIn(-.35f, .35f)
            dragY = (dragY + delta.y / size.height).coerceIn(-.35f, .35f)
        }
    } else Modifier
    val transition = tween<Float>(if (motion) 200 else 0)
    val mounted = animateFloatAsState(if (state == OrbitalState.DORMANT) 0f else 1f, transition, label = "orb light")
    val active = animateFloatAsState(if (state == OrbitalState.ACTIVE) 1f else 0f, transition, label = "orb energy")
    val working = animateFloatAsState(if (state == OrbitalState.WORKING) 1f else 0f, transition, label = "orb charge")
    val failed = animateFloatAsState(if (state == OrbitalState.ERROR) 1f else 0f, transition, label = "orb fault")
    val accent = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val context = LocalContext.current
    val surface = remember(context) {
        if (Build.VERSION.SDK_INT >= 33) runCatching {
            OrbSurface(context.assets.open("shaders/system-orb.agsl").bufferedReader().use { it.readText() })
        }.onFailure { Log.w("SystemOrb", "Using shaded fallback", it) }.getOrNull() else null
    }
    val clock = remember { mutableFloatStateOf(1.2f) }
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        var previous = 0L
        while (true) withFrameNanos { now ->
            if (previous != 0L) {
                val elapsed = ((now - previous) / 1_000_000_000f).coerceAtMost(.05f)
                clock.floatValue += elapsed * (.25f + .45f * mounted.value + 1.2f * active.value + .8f * working.value)
            }
            previous = now
        }
    }
    Canvas(modifier.then(gestures)) {
        val time = clock.floatValue
        val live = mounted.value
        val energy = active.value
        val charge = working.value
        val fault = failed.value
        val palette = when (style) {
            VisualStyle.CHROME -> accent
            VisualStyle.AURORA -> Color(0xFFC0A7FF)
            VisualStyle.EMBER -> Color(0xFFFFB477)
        }
        val tint = lerp(palette, error, fault)
        val orbitColor = lerp(AppText, tint, (energy + charge * .5f + fault).coerceIn(0f, 1f))
        val breathing = if (animated) sin(time * .65f) * (.007f + .012f * energy) else 0f
        val radius = size.minDimension * (.29f + .015f * live + breathing)
        val center = Offset(size.width * .59f + sin(time * .32f) * radius * .06f + tiltX.value * radius * .5f,
            size.height * .51f + parallax().coerceIn(-.1f, .1f) * radius + tiltY.value * radius * .35f)
        val orbitRadius = radius * 1.38f
        fun orbitPoint(angle: Double, layer: Int): Offset {
            val x = cos(angle).toFloat() * orbitRadius
            val y = sin(angle).toFloat() * radius * (.28f + layer * .09f + sin(time * .24f + layer) * .07f)
            return center + Offset(x, y + x * ((if (layer % 2 == 0) -.24f else .35f) + sin(time * .18f + layer) * .14f + tiltY.value))
        }

        drawCircle(Brush.radialGradient(listOf(
            tint.copy(alpha = (.035f + energy * .08f + charge * .05f) * live), Color.Transparent,
        ), center = center, radius = radius * 1.5f), radius * 1.5f, center)

        fun drawOrbits(front: Boolean) {
            for (layer in 0..2) {
                val visibility = if (layer == 0) .12f + .2f * live + .4f * energy else (.1f + .2f * live + .4f * energy + .3f * charge) / layer
                if (visibility < .01f) continue
                val path = Path()
                var drawing = false
                for (step in 0..120) {
                    val angle = step * PI * 2 / 120
                    val visible = (sin(angle) >= 0) == front
                    val point = orbitPoint(angle, layer)
                    if (visible) {
                        if (!drawing) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                    }
                    drawing = visible
                }
                drawPath(path, orbitColor.copy(alpha = visibility * if (front) 1f else .4f), style = Stroke(.75.dp.toPx()))
                val head = time * (if (layer == 0) .52 else -.38) + layer * PI

                for (step in 12 downTo 0) {
                    val angle = head - step * .032 * (if (layer == 0) 1 else -1)
                    if ((sin(angle) >= 0) != front) continue
                    val point = orbitPoint(angle, layer)
                    val alpha = (1f - step / 13f) * visibility
                    drawCircle(orbitColor.copy(alpha = alpha), (.6f + (1f - step / 13f) * 1.3f).dp.toPx(), point)
                }
            }
        }
        fun drawDust(front: Boolean) {
            for (i in 0..71) {
                val angle = i * 2.399963 + time * (.05 + (i % 5) * .012)
                if ((sin(angle) >= 0) != front) continue
                val spread = 1.08f + (i % 7) * .075f
                val point = center + Offset(cos(angle).toFloat() * radius * spread,
                    sin(angle).toFloat() * radius * spread * .55f)
                val depth = .35f + .65f * ((sin(angle) + 1) / 2).toFloat()
                val visibility = (.05f + .16f * live + .42f * energy) * depth
                drawCircle(orbitColor.copy(alpha = visibility), (.35f + depth * .5f).dp.toPx(), point)
            }
        }
        drawDust(false)
        drawOrbits(false)

        if (surface != null && Build.VERSION.SDK_INT >= 33) {
            drawIntoCanvas { canvas -> surface.draw(canvas.nativeCanvas, center, radius, time, live, energy, charge, fault, accent, error, material.value, tiltX.value, tiltY.value) }
        } else {
            drawCircle(Brush.radialGradient(listOf(
                lerp(AppText, tint, energy * .6f).copy(alpha = 1f), Color(0xFF35353C), Color(0xFF121215),
            ), center = center - Offset(radius * .45f, radius * .5f), radius = radius * 1.8f), radius, center)
            val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center - Offset(radius, radius), Size(radius * 2, radius * 2))) }
            clipPath(clip) {
                for (i in 0..5) {
                    drawArc(tint.copy(alpha = (.03f + live * .1f + energy * .1f)), time * 9f + i * 22f, 110f, false,
                        center - Offset(radius * (1.6f - i * .18f), radius * .7f),
                        Size(radius * 2.2f, radius * (1.3f + i * .12f)), style = Stroke((3f + i).dp.toPx()))
                }
            }
            drawCircle(Color.Black.copy(alpha = (1f - live) * .6f), radius, center)
        }
        drawOrbits(true)
        drawDust(true)
        if (charge > .01f) {
            drawArc(tint.copy(alpha = charge * .8f), time * 42f, 65f, false,
                center - Offset(radius * 1.12f, radius * 1.12f), Size(radius * 2.24f, radius * 2.24f),
                style = Stroke(1.2.dp.toPx()))
        }
    }
}

@RequiresApi(33)
private class OrbSurface(source: String) {
    private val shader = RuntimeShader(source)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = this@OrbSurface.shader }

    fun draw(canvas: android.graphics.Canvas, center: Offset, radius: Float, time: Float,
             mounted: Float, active: Float, working: Float, failed: Float, accent: Color, error: Color, style: Float, tiltX: Float, tiltY: Float) {
        shader.setFloatUniform("center", center.x, center.y)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("time", time)
        shader.setFloatUniform("style", style)
        shader.setFloatUniform("tilt", tiltX, tiltY)
        shader.setFloatUniform("state", mounted, active, working, failed)
        shader.setColorUniform("accent", accent.toArgb())
        shader.setColorUniform("failure", error.toArgb())
        canvas.drawCircle(center.x, center.y, radius, paint)
    }
}
