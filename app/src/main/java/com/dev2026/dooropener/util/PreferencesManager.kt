package com.dev2026.dooropener.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Manages local app preferences.
 *
 * Non-sensitive preferences (device names, URLs, timeouts) use plain SharedPreferences.
 * Sensitive credentials (passwords) use EncryptedSharedPreferences backed by AES-256.
 */
class PreferencesManager(context: Context) {

    // Plain preferences for non-sensitive data
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Encrypted preferences for sensitive credentials
    private val securePrefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            SECURE_PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ---- Device settings (non-sensitive) ----

    var targetDeviceId: String?
        get() = prefs.getString(KEY_DEVICE_ID, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    var targetDeviceName: String?
        get() = prefs.getString(KEY_DEVICE_NAME, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_NAME, value).apply()

    // ---- 联掌门户 settings ----

    var lianzhangServerUrl: String?
        get() = prefs.getString(KEY_LZ_SERVER_URL, null)
        set(value) = prefs.edit().putString(KEY_LZ_SERVER_URL, value).apply()

    var lianzhangUsername: String?
        get() = prefs.getString(KEY_LZ_USERNAME, null)
        set(value) = prefs.edit().putString(KEY_LZ_USERNAME, value).apply()

    /** Password stored in EncryptedSharedPreferences — never plaintext on disk */
    var lianzhangPassword: String?
        get() = securePrefs.getString(KEY_LZ_PASSWORD, null)
        set(value) {
            if (value != null) {
                securePrefs.edit().putString(KEY_LZ_PASSWORD, value).apply()
            } else {
                securePrefs.edit().remove(KEY_LZ_PASSWORD).apply()
            }
        }

    var lianzhangDeviceId: String?
        get() = prefs.getString(KEY_LZ_DEVICE_ID, null)
        set(value) = prefs.edit().putString(KEY_LZ_DEVICE_ID, value).apply()

    // ---- Notification preferences (non-sensitive) ----

    var requireConfirmToOpen: Boolean
        get() = prefs.getBoolean(KEY_CONFIRM_OPEN, true)
        set(value) = prefs.edit().putBoolean(KEY_CONFIRM_OPEN, value).apply()

    var callTimeoutSeconds: Int
        get() = prefs.getInt(KEY_CALL_TIMEOUT, DEFAULT_CALL_TIMEOUT)
        set(value) = prefs.edit().putInt(KEY_CALL_TIMEOUT, value).apply()

    var lastOpenDoorTime: Long
        get() = prefs.getLong(KEY_LAST_OPEN_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_OPEN_TIME, value).apply()

    // ---- Event listener mode ----

    var eventListenerMode: String
        get() = prefs.getString(KEY_LISTENER_MODE, MODE_AUTO) ?: MODE_AUTO
        set(value) = prefs.edit().putString(KEY_LISTENER_MODE, value).apply()

    companion object {
        private const val PREFS_NAME = "door_opener_prefs"
        private const val SECURE_PREFS_NAME = "door_opener_secure_prefs"
        private const val KEY_DEVICE_ID = "target_device_id"
        private const val KEY_DEVICE_NAME = "target_device_name"
        private const val KEY_LZ_SERVER_URL = "lz_server_url"
        private const val KEY_LZ_USERNAME = "lz_username"
        private const val KEY_LZ_PASSWORD = "lz_password"
        private const val KEY_LZ_DEVICE_ID = "lz_device_id"
        private const val KEY_CONFIRM_OPEN = "require_confirm"
        private const val KEY_CALL_TIMEOUT = "call_timeout_seconds"
        private const val KEY_LAST_OPEN_TIME = "last_open_door_time"
        private const val KEY_LISTENER_MODE = "event_listener_mode"

        const val MODE_AUTO = "auto"
        const val MODE_MQTT = "mqtt"
        const val MODE_HTTP_POLL = "http_poll"
        const val MODE_NOTIFICATION_LISTENER = "notification_listener"

        const val DEFAULT_CALL_TIMEOUT = 30
    }
}
