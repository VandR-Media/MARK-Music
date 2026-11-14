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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.abs

// Provide the State, not its value: only RenderNode/layer redraws when a sensor
// sample arrives. Recomposition of the entire music library would stutter audio UI.
internal val LocalGlassTilt = compositionLocalOf<State<Offset>> { mutableStateOf(Offset.Zero) }
// A multiplier for UI parallax. 1f preserves the original v0.6.6 sensitivity.
internal val LocalGlassSensitivity = compositionLocalOf { 1f }
internal val LocalGlassDepth = compositionLocalOf { 1f }
// Visual effects run only while the activity is actually foreground/resumed.
internal val LocalVisualActive = compositionLocalOf<State<Boolean>> { mutableStateOf(false) }

@Composable
internal fun GlassMotionHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    val visualActive = remember { mutableStateOf(false) }
    DisposableEffect(context, owner) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var lastUpdate = 0L
        var baseX = Float.NaN
        var baseY = Float.NaN
        var smoothX = 0f
        var smoothY = 0f
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastUpdate < 40L) return  // max 25 updates per second
                lastUpdate = now
                val gx = (event.values[0] / 9.81f).coerceIn(-1f, 1f)
                val gy = (event.values[1] / 9.81f).coerceIn(-1f, 1f)
                if (baseX.isNaN()) {
                    baseX = gx
                    baseY = gy
                }
                // Relative to the hand-held neutral pose, not absolute gravity.
                // This is important for a portrait-locked app.
                smoothX += ((gx - baseX).coerceIn(-.8f, .8f) - smoothX) * .22f
                smoothY += ((gy - baseY).coerceIn(-.8f, .8f) - smoothY) * .22f
                if (abs(smoothX - tilt.value.x) > .006f || abs(smoothY - tilt.value.y) > .006f) {
                    tilt.value = Offset(smoothX, smoothY)
                }
            }
        }
        var registered = false
        fun start() {
            if (!registered && manager != null && sensor != null) {
                baseX = Float.NaN
                baseY = Float.NaN
                smoothX = 0f
                smoothY = 0f
                tilt.value = Offset.Zero
                registered = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
        fun stop() {
            if (registered) manager?.unregisterListener(listener)
            registered = false
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    visualActive.value = true
                    start()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    visualActive.value = false
                    stop()
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            visualActive.value = true
            start()
        }
        onDispose {
            owner.lifecycle.removeObserver(observer)
            stop()
            visualActive.value = false
        }
    }
    CompositionLocalProvider(
        LocalGlassTilt provides tilt,
        LocalVisualActive provides visualActive
    ) { content() }
}

@Composable
internal fun rememberGlassPressed(interactionSource: MutableInteractionSource): State<Boolean> =
    interactionSource.collectIsPressedAsState()

/**
 * Material-like floating depth. No rectangular layer shadow: shadows are
 * provided by the actual rounded/circular shapes in the UI components.
 * Motion is measured in dp and never shifts the clickable layout itself.
 */
@Composable
internal fun Modifier.floatingGlassMotion(
    pressed: Boolean,
    color: Color,
    depth: Float = 1f
): Modifier {
    val tilt = LocalGlassTilt.current
    val sensitivity = LocalGlassSensitivity.current
    val depthSetting = LocalGlassDepth.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) .952f else 1f,
        animationSpec = spring(dampingRatio = .68f, stiffness = 480f),
        label = "glass-press-scale"
    )
    val lift by animateFloatAsState(
        targetValue = if (pressed) 1.1f else -2.0f,
        animationSpec = spring(dampingRatio = .68f, stiffness = 420f),
        label = "glass-press-lift"
    )
    return this.graphicsLayer {
        val raw = tilt.value
        val angle = Offset(raw.x * sensitivity, raw.y * sensitivity)
        val unit = density
        val effectiveDepth = depth * depthSetting
        scaleX = scale
        scaleY = scale
        translationX = angle.x * 15f * unit * effectiveDepth
        translationY = (lift + angle.y * 10f * effectiveDepth) * unit
        rotationY = (-angle.x * 6f * effectiveDepth).coerceIn(-5f, 5f)
        rotationX = (angle.y * 5f * effectiveDepth).coerceIn(-4f, 4f)
        cameraDistance = 64f * density
        // Deliberately NO shadowElevation: that made black rectangles under nav icons.
    }
}
