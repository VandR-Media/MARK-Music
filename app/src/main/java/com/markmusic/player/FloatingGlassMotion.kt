package com.markmusic.player

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.math.abs

/**
 * One gently filtered sensor stream shared by the whole UI, never one sensor
 * listener per button. No sensor permission or background polling is needed.
 */
internal val LocalGlassTilt = compositionLocalOf { Offset.Zero }

@Composable
internal fun GlassMotionHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(context, lifecycleOwner) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var lastUpdate = 0L
        var smoothX = 0f
        var smoothY = 0f
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastUpdate < 65L) return
                lastUpdate = now
                // Small amplitudes avoid shaky graphics and sensor noise.
                val x = (event.values[0] / 9.81f).coerceIn(-0.75f, 0.75f)
                val y = (event.values[1] / 9.81f).coerceIn(-0.75f, 0.75f)
                smoothX += (x - smoothX) * 0.16f
                smoothY += (y - smoothY) * 0.16f
                if (abs(smoothX - tilt.value.x) > .012f || abs(smoothY - tilt.value.y) > .012f) {
                    tilt.value = Offset(smoothX, smoothY)
                }
            }
        }
        var registered = false
        fun start() {
            if (!registered && manager != null && sensor != null) {
                registered = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
        fun stop() {
            if (registered) manager?.unregisterListener(listener)
            registered = false
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        if (lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true) start()
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            stop()
        }
    }
    CompositionLocalProvider(LocalGlassTilt provides tilt.value) { content() }
}

@Composable
internal fun rememberGlassPressed(interactionSource: MutableInteractionSource): State<Boolean> =
    interactionSource.collectIsPressedAsState()

/**
 * Optical elevation + very small gyroscopic parallax. A short spring makes
 * buttons sink under a finger and rise on release; shape and hit area stay fixed.
 */
@Composable
internal fun Modifier.floatingGlassMotion(
    pressed: Boolean,
    color: Color,
    enabled: Boolean = true
): Modifier {
    val tilt = LocalGlassTilt.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.935f else 1f,
        animationSpec = spring(dampingRatio = .64f, stiffness = 620f),
        label = "floating-glass-scale"
    )
    val lift by animateFloatAsState(
        targetValue = if (pressed) 1.5f else -2f,
        animationSpec = spring(dampingRatio = .70f, stiffness = 520f),
        label = "floating-glass-lift"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        translationX = if (enabled) tilt.x * 2.2f else 0f
        translationY = lift + if (enabled) tilt.y * 1.7f else 0f
        rotationZ = if (enabled) tilt.x * 1.2f else 0f
        shadowElevation = if (pressed) 2.dp.toPx() else 10.dp.toPx()
        ambientShadowColor = color.copy(alpha = .42f)
        spotShadowColor = color.copy(alpha = .53f)
    }
}
