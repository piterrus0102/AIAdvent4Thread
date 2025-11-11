package ru.piterrus.aiadvent4thread

import android.content.Context
import android.content.SharedPreferences
import ru.piterrus.aiadvent4thread.data.model.ResponseMode

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "app_preferences",
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val KEY_RESPONSE_MODE = "response_mode"
    }
    
    var responseMode: ResponseMode
        get() = ResponseMode.fromInt(prefs.getInt(KEY_RESPONSE_MODE, 0))
        set(value) {
            prefs.edit().putInt(KEY_RESPONSE_MODE, value.value).apply()
        }
}

