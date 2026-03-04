package com.example.chatcoach.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AppPreferences(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "chatcoach_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences("chatcoach_prefs", Context.MODE_PRIVATE)

    // Service status
    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    // Floating window
    var floatingWindowOpacity: Float
        get() = prefs.getFloat(KEY_FLOAT_OPACITY, 0.9f)
        set(value) = prefs.edit().putFloat(KEY_FLOAT_OPACITY, value).apply()

    var isFloatingWindowEnabled: Boolean
        get() = prefs.getBoolean(KEY_FLOAT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_FLOAT_ENABLED, value).apply()

    // Context settings
    var maxContextMessages: Int
        get() = prefs.getInt(KEY_MAX_CONTEXT, 20)
        set(value) = prefs.edit().putInt(KEY_MAX_CONTEXT, value).apply()

    var summaryThreshold: Int
        get() = prefs.getInt(KEY_SUMMARY_THRESHOLD, 30)
        set(value) = prefs.edit().putInt(KEY_SUMMARY_THRESHOLD, value).apply()

    // Auto trigger
    var isAutoTriggerEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_TRIGGER, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_TRIGGER, value).apply()

    // Cache days
    var cacheDays: Int
        get() = prefs.getInt(KEY_CACHE_DAYS, 7)
        set(value) = prefs.edit().putInt(KEY_CACHE_DAYS, value).apply()

    // Dark mode
    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    // Secure API key storage
    fun saveApiKey(configId: Long, apiKey: String) {
        securePrefs.edit().putString("api_key_$configId", apiKey).apply()
    }

    fun getApiKey(configId: Long): String? {
        return securePrefs.getString("api_key_$configId", null)
    }

    fun removeApiKey(configId: Long) {
        securePrefs.edit().remove("api_key_$configId").apply()
    }

    companion object {
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_FLOAT_OPACITY = "float_opacity"
        private const val KEY_FLOAT_ENABLED = "float_enabled"
        private const val KEY_MAX_CONTEXT = "max_context"
        private const val KEY_SUMMARY_THRESHOLD = "summary_threshold"
        private const val KEY_AUTO_TRIGGER = "auto_trigger"
        private const val KEY_CACHE_DAYS = "cache_days"
        private const val KEY_DARK_MODE = "dark_mode"
    }
}
