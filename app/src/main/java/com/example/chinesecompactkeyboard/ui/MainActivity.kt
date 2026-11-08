package com.example.chinesecompactkeyboard.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.chinesecompactkeyboard.BuildConfig
import com.example.chinesecompactkeyboard.R
import com.example.chinesecompactkeyboard.core.Prefs
import com.example.chinesecompactkeyboard.core.Theme
import com.example.chinesecompactkeyboard.data.AppDatabase
import com.example.chinesecompactkeyboard.data.ClipboardRepository
import com.example.chinesecompactkeyboard.engine.PinyinEngine
import com.example.chinesecompactkeyboard.engine.UserModel
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var clipboard: ClipboardRepository
    private var statusText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Theme.activityThemeRes(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs.get(this)
        clipboard = ClipboardRepository(this)

        statusText = findViewById(R.id.status_text)

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_enable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_choose).setOnClickListener {
            val imm = getSystemService(InputMethodManager::class.java)
            imm?.showInputMethodPicker()
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_test).setOnClickListener {
            startActivity(Intent(this, TestActivity::class.java))
        }

        setupThemeGroup()
        setupSliders()
        setupSwitches()
        setupClipboard()
        setupAbout()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        updateClipboardCount()
        syncUiFromPrefs()
    }

    private fun updateStatus() {
        val imm = getSystemService(InputMethodManager::class.java)
        val enabled = imm?.enabledInputMethodList?.any { it.packageName == packageName } == true
        val selected = Settings.Secure.getString(
            contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
        )?.startsWith(packageName) == true
        val text = when {
            selected -> "✓ Chinese Keyboard is enabled and selected. Tap “Test typing” to try it, or use it in any app."
            enabled -> "Enabled, but not selected yet. Tap “Choose keyboard” and pick Chinese Keyboard."
            else -> "Not enabled yet. Tap “Enable keyboard”, turn on Chinese Keyboard, then tap “Choose keyboard”."
        }
        statusText?.text = text
    }

    private fun setupThemeGroup() {
        val group = findViewById<MaterialButtonToggleGroup>(R.id.theme_group)
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val value = when (checkedId) {
                R.id.btn_theme_light -> Prefs.THEME_LIGHT
                R.id.btn_theme_dark -> Prefs.THEME_DARK
                else -> Prefs.THEME_SYSTEM
            }
            if (prefs.theme != value) {
                prefs.theme = value
                recreate()
            }
        }
    }

    private fun setupSliders() {
        val heightSlider = findViewById<Slider>(R.id.slider_height)
        val heightLabel = findViewById<TextView>(R.id.height_label)
        heightSlider.value = prefs.keyboardHeightDp.toFloat().coerceIn(
            Prefs.MIN_HEIGHT_DP.toFloat(), Prefs.MAX_HEIGHT_DP.toFloat()
        )
        heightLabel.text = getString(R.string.keyboard_height) + ": " + prefs.keyboardHeightDp + " dp"
        heightSlider.addOnChangeListener { _, value, _ ->
            prefs.keyboardHeightDp = value.toInt()
            heightLabel.text = getString(R.string.keyboard_height) + ": " + prefs.keyboardHeightDp + " dp"
        }

        val keySlider = findViewById<Slider>(R.id.slider_key_size)
        val keyLabel = findViewById<TextView>(R.id.key_size_label)
        keySlider.value = prefs.keyScalePercent.toFloat()
        keyLabel.text = getString(R.string.key_size) + ": " + prefs.keyScalePercent + "%"
        keySlider.addOnChangeListener { _, value, _ ->
            prefs.keyScalePercent = value.toInt()
            keyLabel.text = getString(R.string.key_size) + ": " + prefs.keyScalePercent + "%"
        }

        val vibrationSlider = findViewById<Slider>(R.id.slider_vibration)
        val vibrationLabel = findViewById<TextView>(R.id.vibration_label)
        vibrationSlider.value = prefs.vibrationMs.toFloat()
        vibrationLabel.text = getString(R.string.vibration_strength) + ": " + prefs.vibrationMs + " ms"
        vibrationSlider.addOnChangeListener { _, value, _ ->
            prefs.vibrationMs = value.toInt()
            vibrationLabel.text = getString(R.string.vibration_strength) + ": " + prefs.vibrationMs + " ms"
        }
    }

    private fun setupSwitches() {
        val sound = findViewById<MaterialSwitch>(R.id.switch_sound)
        sound.isChecked = prefs.soundEnabled
        sound.setOnCheckedChangeListener { _, checked -> prefs.soundEnabled = checked }

        val vibration = findViewById<MaterialSwitch>(R.id.switch_vibration)
        vibration.isChecked = prefs.vibrationEnabled
        vibration.setOnCheckedChangeListener { _, checked ->
            prefs.vibrationEnabled = checked
            findViewById<Slider>(R.id.slider_vibration).isEnabled = checked
        }
        findViewById<Slider>(R.id.slider_vibration).isEnabled = prefs.vibrationEnabled

        val suggestions = findViewById<MaterialSwitch>(R.id.switch_suggestions)
        suggestions.isChecked = prefs.pinyinSuggestions
        suggestions.setOnCheckedChangeListener { _, checked -> prefs.pinyinSuggestions = checked }

        val bar = findViewById<MaterialSwitch>(R.id.switch_candidate_bar)
        bar.isChecked = prefs.candidateBarVisible
        bar.setOnCheckedChangeListener { _, checked -> prefs.candidateBarVisible = checked }

        val traditional = findViewById<MaterialSwitch>(R.id.switch_traditional)
        traditional.isChecked = prefs.traditional
        traditional.setOnCheckedChangeListener { _, checked -> prefs.traditional = checked }

        val capture = findViewById<MaterialSwitch>(R.id.switch_clipboard)
        capture.isChecked = prefs.clipboardCapture
        capture.setOnCheckedChangeListener { _, checked -> prefs.clipboardCapture = checked }
    }

    private fun setupClipboard() {
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_manage_clipboard).setOnClickListener {
            startActivity(Intent(this, ClipboardActivity::class.java))
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_clear_clipboard).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.clear_all)
                .setMessage("Delete all saved clipboard items?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.clear_all) { _, _ ->
                    clipboard.clear()
                    Toast.makeText(this, "Clipboard history cleared", Toast.LENGTH_SHORT).show()
                    updateClipboardCount()
                }
                .show()
        }
    }

    private fun setupAbout() {
        val about = findViewById<TextView>(R.id.about_text)
        about.text = "Offline pinyin engine — no internet needed.\nLoading dictionary…"
        lifecycleScope.launch {
            val stats = withContext(Dispatchers.IO) {
                try {
                    val engine = PinyinEngine.get(applicationContext)
                    "${engine.wordCount} entries, ${engine.syllableCount} syllables"
                } catch (t: Throwable) {
                    "dictionary unavailable"
                }
            }
            about.text = "Offline pinyin engine — no internet needed.\n" +
                    "Dictionary: $stats.\n" +
                    "Simplified ⇄ Traditional conversion: OpenCC tables.\n" +
                    "Dictionary data: Rime luna-pinyin, Rime essay, mozillazg/phrase-pinyin-data (MIT).\n" +
                    "Clipboard history is stored only on this device (SQLite).\n" +
                    "Version ${BuildConfig.VERSION_NAME}"
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_reset_learning).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.reset_learning)
                .setMessage("Forget the words and word pairs learned from your typing?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    UserModel(applicationContext).clearLearning()
                    Toast.makeText(this, "Learning data cleared", Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    private fun updateClipboardCount() {
        val label = findViewById<TextView>(R.id.clipboard_count)
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) { AppDatabase.get(applicationContext).clipboardDao().count() }
            label.text = when (count) {
                0 -> "No saved items yet."
                1 -> "1 saved item (newest first, up to ${Prefs.CLIPBOARD_LIMIT})."
                else -> "$count saved items (newest first, up to ${Prefs.CLIPBOARD_LIMIT})."
            }
        }
    }

    private fun syncUiFromPrefs() {
        findViewById<MaterialButtonToggleGroup>(R.id.theme_group).check(
            when (prefs.theme) {
                Prefs.THEME_LIGHT -> R.id.btn_theme_light
                Prefs.THEME_DARK -> R.id.btn_theme_dark
                else -> R.id.btn_theme_system
            }
        )
        findViewById<MaterialSwitch>(R.id.switch_sound).isChecked = prefs.soundEnabled
        findViewById<MaterialSwitch>(R.id.switch_vibration).isChecked = prefs.vibrationEnabled
        findViewById<MaterialSwitch>(R.id.switch_suggestions).isChecked = prefs.pinyinSuggestions
        findViewById<MaterialSwitch>(R.id.switch_candidate_bar).isChecked = prefs.candidateBarVisible
        findViewById<MaterialSwitch>(R.id.switch_traditional).isChecked = prefs.traditional
        findViewById<MaterialSwitch>(R.id.switch_clipboard).isChecked = prefs.clipboardCapture
    }
}
