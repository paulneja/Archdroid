package io.github.paulneja.archdroid.ui.components.liquid

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt

/** Light direction in screen degrees (-90 = top). */
@Composable
fun rememberGravityAngle(stepDegrees: Float = 3f, fallbackDegrees: Float = -90f): State<Float> {
    val context = LocalContext.current
    val angle = remember { mutableFloatStateOf(fallbackDegrees) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val sx = event.values[0]
                val sy = -event.values[1]
                if (sx * sx + sy * sy < 0.9f) return // lying flat: keep the last angle
                val degrees = atan2(sy, sx) * 180f / PI.toFloat()
                angle.floatValue = (degrees / stepDegrees).roundToInt() * stepDegrees
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (manager != null && sensor != null) {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { manager?.unregisterListener(listener) }
    }
    return angle
}
