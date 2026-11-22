package com.markmusic.player

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.markmusic.player.library.YandexWebNavigationRules

/**
 * Opt-in official website container only.
 * No JS bridge, session extraction, request interception, stream resolution
 * or user-agent impersonation. External URLs go to the system browser.
 */
internal class YandexWebMusicActivity : Activity() {
    private var browser: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.rgb(11, 23, 48))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val close = Button(this).apply {
            text = "Закрыть"
            setOnClickListener { finish() }
        }
        val title = TextView(this).apply {
            text = "Яндекс Музыка · веб-режим"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 15f
            setPadding(10, 0, 10, 0)
        }
        val openExternal = Button(this).apply {
            text = "Браузер"
            setOnClickListener { launchExternal(YandexWebNavigationRules.START_URL) }
        }
        top.addView(close)
        top.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(openExternal)
        container.addView(top)

        val warning = TextView(this).apply {
            text = "Официальный сайт Яндекса. Вход в веб-режиме отдельный от MARK Audio. " +
                "Воспроизведение и фоновая работа зависят от Яндекса и Android WebView."
            setTextColor(android.graphics.Color.WHITE)
            textSize = 12f
            setPadding(14, 6, 14, 6)
        }
        container.addView(warning)
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
        }
        container.addView(progress, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 3))
        val web = WebView(this)
        browser = web
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            mediaPlaybackRequiresUserGesture = true
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) android.view.View.GONE
                    else android.view.View.VISIBLE
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?, request: WebResourceRequest?
            ): Boolean {
                val target = request?.url?.toString() ?: return true
                if (YandexWebNavigationRules.isAllowed(target)) return false
                if (request.isForMainFrame && YandexWebNavigationRules.isExternalHttps(target)) {
                    launchExternal(target)
                }
                return true
            }

            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    warning.text = "Страница недоступна в WebView. " +
                        "Можно открыть официальный сайт кнопкой «Браузер»."
                }
            }
        }
        container.addView(web, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(container)
        web.loadUrl(YandexWebNavigationRules.START_URL)
    }

    private fun launchExternal(address: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address)))
        }
    }

    @Deprecated("Deprecated by Android; used for WebView history on API 26+.")
    override fun onBackPressed() {
        if (browser?.canGoBack() == true) browser?.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        browser?.apply {
            stopLoading()
            webChromeClient = null
            webViewClient = WebViewClient()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        browser = null
        super.onDestroy()
    }
}
