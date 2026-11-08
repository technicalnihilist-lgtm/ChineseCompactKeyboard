package com.example.chinesecompactkeyboard.core

import android.content.Context
import android.content.res.Configuration

/** Colours for the keyboard surface. Two palettes, one for each theme. */
data class KeyboardPalette(
    val dark: Boolean,
    val background: Int,
    val keyBackground: Int,
    val keyPressed: Int,
    val keyText: Int,
    val functionKeyBackground: Int,
    val functionKeyPressed: Int,
    val functionKeyText: Int,
    val accent: Int,
    val divider: Int,
    val hint: Int,
    val candidateText: Int,
    val candidateSelectedBackground: Int,
    val panelBackground: Int,
    val panelItem: Int,
    val popupBackground: Int,
    val popupText: Int,
    val shadow: Int
)

object Theme {

    private val light = KeyboardPalette(
        dark = false,
        background = 0xFFFFFFFF.toInt(),
        keyBackground = 0xFFF1F2F4.toInt(),
        keyPressed = 0xFFDDE1E6.toInt(),
        keyText = 0xFF1B1D21.toInt(),
        functionKeyBackground = 0xFFE4E6E9.toInt(),
        functionKeyPressed = 0xFFD0D4D9.toInt(),
        functionKeyText = 0xFF3A3D42.toInt(),
        accent = 0xFF2F6FED.toInt(),
        divider = 0xFFE7E9EC.toInt(),
        hint = 0xFF8A9099.toInt(),
        candidateText = 0xFF1B1D21.toInt(),
        candidateSelectedBackground = 0xFFE8EFFD.toInt(),
        panelBackground = 0xFFFFFFFF.toInt(),
        panelItem = 0xFFF5F6F8.toInt(),
        popupBackground = 0xFF2B2F36.toInt(),
        popupText = 0xFFFFFFFF.toInt(),
        shadow = 0x33000000
    )

    private val dark = KeyboardPalette(
        dark = true,
        background = 0xFF121316.toInt(),
        keyBackground = 0xFF2C2F34.toInt(),
        keyPressed = 0xFF3E4248.toInt(),
        keyText = 0xFFF2F3F5.toInt(),
        functionKeyBackground = 0xFF23262A.toInt(),
        functionKeyPressed = 0xFF34383D.toInt(),
        functionKeyText = 0xFFD5D8DC.toInt(),
        accent = 0xFF7EA6FF.toInt(),
        divider = 0xFF23262A.toInt(),
        hint = 0xFF8B9099.toInt(),
        candidateText = 0xFFF2F3F5.toInt(),
        candidateSelectedBackground = 0xFF2A3346.toInt(),
        panelBackground = 0xFF121316.toInt(),
        panelItem = 0xFF1E2125.toInt(),
        popupBackground = 0xFFE9EBEE.toInt(),
        popupText = 0xFF15171A.toInt(),
        shadow = 0x66000000
    )

    fun palette(isDark: Boolean): KeyboardPalette = if (isDark) dark else light

    /** Resolve the "system" setting against the current configuration. */
    fun isDark(context: Context): Boolean {
        return when (Prefs.get(context).theme) {
            Prefs.THEME_DARK -> true
            Prefs.THEME_LIGHT -> false
            else -> {
                val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                mode == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    fun paletteFor(context: Context): KeyboardPalette = palette(isDark(context))

    /** Theme resource used by the activities. */
    fun activityThemeRes(context: Context): Int = when (Prefs.get(context).theme) {
        Prefs.THEME_DARK -> com.example.chinesecompactkeyboard.R.style.Theme_ChineseKeyboard_Dark
        Prefs.THEME_LIGHT -> com.example.chinesecompactkeyboard.R.style.Theme_ChineseKeyboard_Light
        else -> com.example.chinesecompactkeyboard.R.style.Theme_ChineseKeyboard_System
    }
}
