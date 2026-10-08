package com.tesaduf.app.ui.home

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.abs

/**
 * Where the avatar should look based on how the phone is tilted (x, y in -1..1), or null
 * when the phone is held still and upright. Low-pass filtered; the sensor only runs while
 * the screen is resumed.
 */
@Composable
fun rememberTiltGaze(): State<Offset?> {
    val context = LocalContext.current
    val gaze = remember { mutableStateOf<Offset?>(null) }
    LifecycleResumeEffect(Unit) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var fx = 0f
        var fy = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Portrait: x = left/right tilt, y = forward/back (≈9.8 upright).
                fx += (-event.values[0] / 4f - fx) * 0.15f
                fy += ((6.5f - event.values[1]) / 4f - fy) * 0.15f
                val x = fx.coerceIn(-1f, 1f)
                val y = fy.coerceIn(-1f, 1f)
                val next = if (abs(x) < 0.12f && abs(y) < 0.12f) null else Offset(x, y)
                val prev = gaze.value
                // Only publish meaningful changes (avoid churn).
                if (prev == null || next == null || abs(prev.x - next.x) + abs(prev.y - next.y) > 0.06f) gaze.value = next
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onPauseOrDispose {
            manager?.unregisterListener(listener)
            gaze.value = null
        }
    }
    return gaze
}
