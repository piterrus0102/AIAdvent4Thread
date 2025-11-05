package ru.piterrus.aiadvent4thread

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "app_preferences",
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val KEY_FIXED_RESPONSE_ENABLED = "fixed_response_enabled"
    }
    
    var isFixedResponseEnabled: Boolean
        get() = prefs.getBoolean(KEY_FIXED_RESPONSE_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_FIXED_RESPONSE_ENABLED, value).apply()
        }
}

