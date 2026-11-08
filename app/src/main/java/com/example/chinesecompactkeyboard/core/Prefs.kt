package com.example.chinesecompactkeyboard.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Single source of truth for every user setting. Both the IME service and the
 * settings screens read and write through this class, so a change made in
 * Settings takes effect in the keyboard immediately.
 */
class Prefs private constructor(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var theme: String
        get() = sp.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
        set(value) = sp.edit().putString(KEY_THEME, value).apply()

    /** Total keyboard height in dp (candidate bar included). */
    var keyboardHeightDp: Int
        get() = sp.getInt(KEY_HEIGHT, DEFAULT_HEIGHT_DP).coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)
        set(value) = sp.edit().putInt(KEY_HEIGHT, value.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)).apply()

    /** Key size as a percentage of the base layout (85 % – 125 %). */
    var keyScalePercent: Int
        get() = sp.getInt(KEY_SCALE, 100).coerceIn(MIN_SCALE, MAX_SCALE)
        set(value) = sp.edit().putInt(KEY_SCALE, value.coerceIn(MIN_SCALE, MAX_SCALE)).apply()

    var soundEnabled: Boolean
        get() = sp.getBoolean(KEY_SOUND, true)
        set(value) = sp.edit().putBoolean(KEY_SOUND, value).apply()

    var vibrationEnabled: Boolean
        get() = sp.getBoolean(KEY_VIBRATE, true)
        set(value) = sp.edit().putBoolean(KEY_VIBRATE, value).apply()

    var vibrationMs: Int
        get() = sp.getInt(KEY_VIBRATE_MS, 18).coerceIn(5, 60)
        set(value) = sp.edit().putInt(KEY_VIBRATE_MS, value.coerceIn(5, 60)).apply()

    /** Context predictions + sentence composition. */
    var pinyinSuggestions: Boolean
        get() = sp.getBoolean(KEY_SUGGESTIONS, true)
        set(value) = sp.edit().putBoolean(KEY_SUGGESTIONS, value).apply()

    var candidateBarVisible: Boolean
        get() = sp.getBoolean(KEY_CANDIDATE_BAR, true)
        set(value) = sp.edit().putBoolean(KEY_CANDIDATE_BAR, value).apply()

    /** Read the system clipboard when the keyboard is shown and store new items. */
    var clipboardCapture: Boolean
        get() = sp.getBoolean(KEY_CLIP_CAPTURE, true)
        set(value) = sp.edit().putBoolean(KEY_CLIP_CAPTURE, value).apply()

    var chineseMode: Boolean
        get() = sp.getBoolean(KEY_CHINESE, true)
        set(value) = sp.edit().putBoolean(KEY_CHINESE, value).apply()

    var traditional: Boolean
        get() = sp.getBoolean(KEY_TRADITIONAL, false)
        set(value) = sp.edit().putBoolean(KEY_TRADITIONAL, value).apply()

    var capsLock: Boolean
        get() = sp.getBoolean(KEY_CAPS, false)
        set(value) = sp.edit().putBoolean(KEY_CAPS, value).apply()

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sp.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val FILE = "keyboard_prefs"

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        const val MIN_HEIGHT_DP = 170
        const val MAX_HEIGHT_DP = 340
        const val DEFAULT_HEIGHT_DP = 232
        const val MIN_SCALE = 85
        const val MAX_SCALE = 125
        const val CLIPBOARD_LIMIT = 100

        private const val KEY_THEME = "theme"
        private const val KEY_HEIGHT = "keyboard_height"
        private const val KEY_SCALE = "key_scale"
        private const val KEY_SOUND = "sound"
        private const val KEY_VIBRATE = "vibrate"
        private const val KEY_VIBRATE_MS = "vibrate_ms"
        private const val KEY_SUGGESTIONS = "suggestions"
        private const val KEY_CANDIDATE_BAR = "candidate_bar"
        private const val KEY_CLIP_CAPTURE = "clipboard_capture"
        private const val KEY_CHINESE = "chinese_mode"
        private const val KEY_TRADITIONAL = "traditional"
        private const val KEY_CAPS = "caps_lock"

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context).also { instance = it }
            }
    }
}
