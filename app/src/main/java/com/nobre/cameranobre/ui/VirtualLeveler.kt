package com.nobre.cameranobre.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

@Composable
fun VirtualLeveler(
    modifier: Modifier = Modifier,
    toleranceDegrees: Float = 1.2f
) {
    val context = LocalContext.current
    var roll by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                val calculatedRoll = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
                val calculatedPitch = Math.toDegrees(atan2(z.toDouble(), sqrt((x * x + y * y).toDouble()))).toFloat()

                roll = calculatedRoll
                pitch = calculatedPitch
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensor?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    val isLevel = abs(roll) <= toleranceDegrees || abs(roll - 180f) <= toleranceDegrees
    val isPitchLevel = abs(pitch) <= 5.0f

    val levelColor by animateColorAsState(
        targetValue = if (isLevel && isPitchLevel) Color(0xFF00E676) else Color.White.copy(alpha = 0.6f),
        label = "levelColor"
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(160.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val lineLength = 50.dp.toPx()

            drawLine(
                color = levelColor,
                start = Offset(center.x - lineLength, center.y),
                end = Offset(center.x - 12.dp.toPx(), center.y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = levelColor,
                start = Offset(center.x + 12.dp.toPx(), center.y),
                end = Offset(center.x + lineLength, center.y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = levelColor,
                radius = 3.dp.toPx(),
                center = center
            )
        }
    }
}
