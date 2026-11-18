package com.markmusic.player.visual

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.SystemClock
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

/**
 * Experimental true-WebGL2 visual engine with the existing MARK Audio player.
 * ExoPlayer remains the ONLY audio playback source. This WebView receives
 * measurements from AudioMeterBus + StereoSpectrumWorker, and deliberately
 * has no local file picker, demo controls, mic API or second audio decoder.
 *
 * The tested standalone renderer is vendored as a network-free app asset.
 * This integration is in a feature branch, not the stable MARK Audio release.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun MarkVisualV2WebView(
    analog: Boolean,
    playing: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val levels by AudioMeterBus.levels.collectAsState()
    val bands by AudioMeterBus.frequencyBands.collectAsState()
    var loaded by remember { mutableStateOf(false) }

    val webView = remember(context, analog) {
        WebView(context).apply {
            setBackgroundColor(Color.BLACK)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.allowFileAccess = true // only bundled file:///android_asset/
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
            settings.allowContentAccess = false
            settings.blockNetworkLoads = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Local standalone file controls are removed in embedded mode:
                    // the app's ExoPlayer owns the one and only music source.
                    val target = if (analog) "analog-studio" else "spectrum-matrix"
                    evaluateJavascript(
                        """
                        (function(){
                          var style=document.createElement('style');
                          // Inside the Kotlin player this is a full-bleed instrument, NOT
                          // the Studio editor with offscreen controls and a 350px canvas.
                          style.textContent=[
                            'html,body{width:100%!important;height:100%!important;min-width:0!important;min-height:0!important;margin:0!important;padding:0!important;overflow:hidden!important;background:#02040c!important;}',
                            'body .studio{width:100%!important;height:100%!important;max-width:none!important;margin:0!important;padding:0!important;overflow:hidden!important;}',
                            'body .studio > :not(.canvas-frame){display:none!important;}',
                            'body .canvas-frame{display:block!important;position:relative!important;width:100%!important;height:100%!important;min-height:0!important;max-height:none!important;margin:0!important;padding:0!important;border:0!important;border-radius:0!important;box-shadow:none!important;overflow:hidden!important;}',
                            'body #visualCanvas{display:block!important;width:100%!important;height:100%!important;touch-action:none!important;}',
                            '.music-bar,.audio-panel,.demo-switch{display:none!important;}'
                          ].join('');
                          document.head.appendChild(style);
                          document.querySelectorAll('#audioFile,#openMusic,#musicToggle,#demo')
                            .forEach(function(e){e.disabled=true;e.remove();});
                          window.__MARK_NATIVE_PENDING__=window.__MARK_NATIVE_PENDING__||null;
                          var attempts=0;
                          var interval=setInterval(function(){
                            attempts++;
                            if(window.__MARK_VISUAL_READY__ && window.MARK_VISUAL_API){
                              clearInterval(interval);
                              var mode=document.getElementById('mode');
                              if(mode && mode.value!=='$target'){
                                mode.value='$target';
                                mode.dispatchEvent(new Event('change',{bubbles:true}));
                              }
                              // Re-measure camera bounds after full-bleed CSS/layout.
                              // Otherwise the scene keeps the standalone Studio aspect ratio.
                              window.dispatchEvent(new Event('resize'));
                              setTimeout(function(){
                                if(window.MARK_VISUAL_API){
                                  window.dispatchEvent(new Event('resize'));
                                  window.MARK_VISUAL_API.view('front');
                                }
                              },200);
                              // apply the latest actual ExoPlayer meter frame
                              // once renderer and mode exist (late WebView startup)
                              var pending=window.__MARK_NATIVE_PENDING__;
                              if(pending){
                                window.MARK_VISUAL_API.renderFrame(pending);
                              }
                            }else if(attempts>=200){clearInterval(interval);}
                          },75);
                        })();
                        """.trimIndent(),
                        null
                    )
                    loaded = true
                }
            }
            loadUrl("file:///android_asset/mark_visual_engine_v2.html#embedded")
        }
    }

    DisposableEffect(webView) {
        onDispose {
            loaded = false
            webView.stopLoading()
            webView.destroy()
        }
    }

    LaunchedEffect(webView, loaded, levels, bands, playing) {
        if (!loaded) return@LaunchedEffect
        val frame = MarkVisualV2Frame.serialize(
            levels = levels,
            bands = bands,
            playing = playing,
            timestampMs = SystemClock.elapsedRealtime()
        )
        // This is a model-data call, not playback. It cannot open a track,
        // access the microphone or create any WebAudio source.
        webView.evaluateJavascript(
            "(function(){var frame=$frame;window.__MARK_NATIVE_PENDING__=frame;" +
                "if(window.__MARK_VISUAL_READY__ && window.MARK_VISUAL_API){" +
                "window.MARK_VISUAL_API.renderFrame(frame);}})();",
            null
        )
    }

    AndroidView(factory = { webView }, modifier = modifier.fillMaxSize())
}
