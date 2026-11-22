package com.markmusic.player.library

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Music API uses an independent AES-GCM key and storage namespace from Yandex ID.
 * Neither verification code nor access token is written into plaintext preferences.
 */
internal class YandexMusicVault(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "mark_yandex_music_api_encrypted", Context.MODE_PRIVATE
    )

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        generator.init(KeyGenParameterSpec.Builder(
            KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false).build())
        return generator.generateKey()
    }

    fun save(name: String, text: String) {
        require(name in KEYS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        preferences.edit().putString(name, Base64.encodeToString(data, Base64.NO_WRAP)).apply()
    }

    fun read(name: String): String? {
        require(name in KEYS)
        val encoded = preferences.getString(name, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            require(bytes.size > 12 + 16)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE, key(),
                GCMParameterSpec(128, bytes.copyOfRange(0, 12))
            )
            cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
        }.getOrElse { remove(name); null }
    }

    fun remove(name: String) {
        require(name in KEYS)
        preferences.edit().remove(name).apply()
    }

    private companion object {
        const val KEY_ALIAS = "mark_audio_yandex_music_api_aes_gcm_v1"
        val KEYS = setOf("pending", "token", "user_token", "major_pending", "major_token")
    }
}
