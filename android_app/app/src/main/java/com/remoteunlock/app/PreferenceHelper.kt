package com.remoteunlock.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages persistent storage of configuration settings like the laptop's
 * IP address, HTTP port, and secret bearer authentication token.
 */
class PreferenceHelper(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var laptopIp: String
        get() = prefs.getString(KEY_LAPTOP_IP, "192.168.1.2").orEmpty()
        set(value) = prefs.edit().putString(KEY_LAPTOP_IP, value.trim()).apply()

    var httpPort: Int
        get() = prefs.getInt(KEY_HTTP_PORT, 5000)
        set(value) = prefs.edit().putInt(KEY_HTTP_PORT, value).apply()

    var secretToken: String
        get() = prefs.getString(KEY_SECRET_TOKEN, "change_this_to_a_secure_token_123456").orEmpty()
        set(value) = prefs.edit().putString(KEY_SECRET_TOKEN, value.trim()).apply()

    var windowsPin: String
        get() = prefs.getString(KEY_WINDOWS_PIN, "8340").orEmpty()
        set(value) = prefs.edit().putString(KEY_WINDOWS_PIN, value.trim()).apply()

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    companion object {
        private const val PREFS_NAME = "remote_laptop_unlock_prefs"
        private const val KEY_LAPTOP_IP = "laptop_ip"
        private const val KEY_HTTP_PORT = "http_port"
        private const val KEY_SECRET_TOKEN = "secret_token"
        private const val KEY_WINDOWS_PIN = "windows_pin"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
    }
}
