package com.tesaduf.app.ui.home

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.sqrt

/**
 * Calls [onShake] on a deliberate shake (two strong jolts within ~0.7 s). The sensor is
 * registered only while the screen is resumed, so it costs nothing in the background.
 */
@Composable
fun ShakeToStart(enabled: Boolean, onShake: () -> Unit) {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onShake)
    LifecycleResumeEffect(enabled) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var firstJolt = 0L
        var lastFire = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
                val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                if (g < THRESHOLD_G) return
                val now = System.currentTimeMillis()
                if (now - lastFire < COOLDOWN_MS) return
                if (now - firstJolt in MIN_GAP_MS..WINDOW_MS) {
                    lastFire = now
                    firstJolt = 0L
                    callback.value()
                } else if (now - firstJolt > WINDOW_MS) {
                    firstJolt = now
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (enabled && sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onPauseOrDispose { manager?.unregisterListener(listener) }
    }
}

private const val THRESHOLD_G = 2.4f
private const val MIN_GAP_MS = 120L
private const val WINDOW_MS = 700L
private const val COOLDOWN_MS = 3_000L
