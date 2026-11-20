package com.markmusic.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.markmusic.player.library.YandexIdAccountRepository
import com.markmusic.player.library.YandexLoginResult
import kotlinx.coroutines.launch

/**
 * Exported ONLY to receive the registered OAuth redirect URI.
 * Never passes a token or verification code through intents or logs.
 */
class YandexIdCallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val callbackUrl = intent?.dataString
        if (callbackUrl.isNullOrBlank()) {
            finish()
            return
        }
        lifecycleScope.launch {
            val outcome = YandexIdAccountRepository(applicationContext)
                .completeRedirect(callbackUrl)
            val resultText = when (outcome) {
                is YandexLoginResult.Connected ->
                    "Яндекс ID подключён. Для музыки ещё требуется доступ к каталогу."
                is YandexLoginResult.Unavailable -> outcome.reason
            }
            startActivity(Intent(this@YandexIdCallbackActivity, MainActivity::class.java).apply {
                action = MainActivity.ACTION_YANDEX_ID_RESULT
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_YANDEX_ID_MESSAGE, resultText)
            })
            finish()
        }
    }
}
