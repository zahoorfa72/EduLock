package com.edulock.app

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Local parent gate. PIN verifiers use PBKDF2 with a per-install random salt;
 * the PIN itself is never persisted. This protects local settings UI, not the
 * Android device against uninstall, data clearing, or a factory reset.
 */
internal object ParentSecurity {
    private const val PREFS = "edulock_parent_security"
    private const val UI_PREFS = "edulock_parent_security_ui"
    private const val SALT = "pin_salt"
    private const val HASH = "pin_hash"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH = 256

    fun isPinSet(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(HASH)

    fun isBiometricEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("biometric_enabled", false)

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("biometric_enabled", enabled).apply()
    }

    fun shouldShowSetupSuggestion(context: Context): Boolean =
        context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
            .getBoolean("show_setup_suggestion", true)

    fun setShowSetupSuggestion(context: Context, show: Boolean) {
        context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("show_setup_suggestion", show).apply()
    }

    fun setPin(context: Context, pin: CharArray) {
        require(pin.size in 4..12 && pin.all { it.isDigit() }) { "PIN must contain 4–12 digits." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
        pin.fill('\u0000')
    }

    fun verifyPin(context: Context, pin: CharArray): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saltText = prefs.getString(SALT, null) ?: return false
        val hashText = prefs.getString(HASH, null) ?: return false
        return try {
            val expected = Base64.decode(hashText, Base64.NO_WRAP)
            val actual = derive(pin, Base64.decode(saltText, Base64.NO_WRAP))
            MessageDigest.isEqual(expected, actual)
        } catch (_: Exception) {
            false
        } finally {
            pin.fill('\u0000')
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun derive(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, ITERATIONS, KEY_LENGTH)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
