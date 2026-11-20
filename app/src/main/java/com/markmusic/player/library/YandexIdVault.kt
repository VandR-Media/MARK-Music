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
 * Keystore-backed encrypted storage. No password, OAuth verifier, state,
 * access token, or refresh token is written as plaintext preferences.
 * android:allowBackup=false ensures the encrypted blobs are not migrated
 * without their corresponding device key.
 */
internal class YandexIdVault(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "mark_yandex_id_encrypted", Context.MODE_PRIVATE
    )

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    fun save(name: String, plaintext: String) {
        require(name == "pending" || name == "account")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        preferences.edit().putString(name, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }

    fun read(name: String): String? {
        require(name == "pending" || name == "account")
        val encoded = preferences.getString(name, null) ?: return null
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(payload.size > 12 + 16)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, 12))
            )
            cipher.doFinal(payload.copyOfRange(12, payload.size)).toString(Charsets.UTF_8)
        }.getOrElse {
            // Fail closed if encrypted values were tampered with or the key was lost.
            remove(name)
            null
        }
    }

    fun remove(name: String) {
        require(name == "pending" || name == "account")
        preferences.edit().remove(name).apply()
    }

    companion object {
        private const val KEY_ALIAS = "mark_audio_yandex_id_aes_gcm_v1"
    }
}
