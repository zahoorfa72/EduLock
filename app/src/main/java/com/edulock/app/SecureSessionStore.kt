package com.edulock.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val accountRole: String,
    val expiresAtMillis: Long
)

internal class SecureSessionStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): AuthSession? {
        val packed = prefs.getString(SESSION, null) ?: return null
        return try {
            val decoded = Base64.decode(packed, Base64.NO_WRAP)
            require(decoded.size > IV_BYTES)
            val iv = decoded.copyOfRange(0, IV_BYTES)
            val encrypted = decoded.copyOfRange(IV_BYTES, decoded.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val clear = cipher.doFinal(encrypted).toString(Charsets.UTF_8)
            val json = JSONObject(clear)
            AuthSession(
                accessToken = json.getString("access_token"),
                refreshToken = json.getString("refresh_token"),
                userId = json.getString("user_id"),
                accountRole = json.optString("account_role", "parent"),
                expiresAtMillis = json.optLong("expires_at", 0L)
            )
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun save(session: AuthSession) {
        val json = JSONObject()
            .put("access_token", session.accessToken)
            .put("refresh_token", session.refreshToken)
            .put("user_id", session.userId)
            .put("account_role", session.accountRole)
            .put("expires_at", session.expiresAtMillis)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        check(prefs.edit().putString(SESSION, Base64.encodeToString(packed, Base64.NO_WRAP)).commit()) {
            "Could not securely save the session"
        }
    }

    fun clear() {
        prefs.edit().remove(SESSION).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "edulock_secure_session"
        const val SESSION = "encrypted_session"
        const val KEY_ALIAS = "edulock_session_key_v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
