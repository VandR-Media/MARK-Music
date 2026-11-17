package com.markmusic.player.visual

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.markmusic.player.audio.AudioMeterBus
import com.markmusic.player.audio.normalizeMeterRms

/**
 * Native MARK Audio -> MARK 3D Studio visualizer bridge.
 *
 * Bundles the six artist-approved reference-based visualizer layouts, runs
 * without CDN, network, microphone or extra permissions, and consumes the
 * existing ExoPlayer PCM meter plus independent six-band FFT.
 *
 * NOTE: this is a hybrid 2D/CSS-depth preview built from 3D-rendered artwork,
 * not six independent real-time GL meshes. The artistic acceptance gate
 * remains separate from the correctness of PCM band measurements.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkStereoCollection(
    playing: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val levels by AudioMeterBus.levels.collectAsState()
    val bands by AudioMeterBus.frequencyBands.collectAsState()
    var loaded by remember { mutableStateOf(false) }

    val webView = remember(context) {
        WebView(context).apply {
            setBackgroundColor(AndroidColor.BLACK)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    loaded = true
                }
            }
            loadUrl("file:///android_asset/mark_stereo_visual_collection.html#embedded")
        }
    }

    DisposableEffect(webView) {
        onDispose {
            loaded = false
            webView.stopLoading()
            webView.destroy()
        }
    }

    // This callback sends measured values from ExoPlayer; never synthesize
    // channel levels or bass while music is playing. The web renderer holds
    // its own short interpolation/release frame for smooth 60fps animation.
    LaunchedEffect(webView, loaded, levels, bands, playing) {
        if (!loaded) return@LaunchedEffect
        val six = listOf(
            bands.left.low, bands.left.mid, bands.left.high,
            bands.right.low, bands.right.mid, bands.right.high
        ).joinToString(",") { normalizeMeterRms(it.toDouble()).toString() }
        val left = if (playing) levels.left.coerceIn(0f, 1f) else 0f
        val right = if (playing) levels.right.coerceIn(0f, 1f) else 0f
        webView.evaluateJavascript(
            "window.markVisualFrame&&window.markVisualFrame({bands:[$six],left:$left,right:$right});",
            null
        )
    }

    AndroidView(
        factory = { webView },
        modifier = modifier.fillMaxSize()
    )
}
