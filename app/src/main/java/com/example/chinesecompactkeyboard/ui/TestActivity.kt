package com.example.chinesecompactkeyboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.chinesecompactkeyboard.R
import com.example.chinesecompactkeyboard.core.Theme
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

/** A sandbox field for trying the keyboard without leaving the app. */
class TestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Theme.activityThemeRes(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationIcon(android.R.drawable.ic_menu_revert)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val input = findViewById<EditText>(R.id.test_input)
        findViewById<MaterialButton>(R.id.btn_clear).setOnClickListener { input.text.clear() }
        findViewById<MaterialButton>(R.id.btn_copy).setOnClickListener {
            val text = input.text.toString()
            if (text.isEmpty()) {
                Toast.makeText(this, "Nothing to copy", Toast.LENGTH_SHORT).show()
            } else {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("test", text))
                Toast.makeText(this, "Copied — open the keyboard's ☰ panel to see it", Toast.LENGTH_LONG).show()
            }
        }

        input.requestFocus()
        Handler(Looper.getMainLooper()).postDelayed({
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 250)
    }
}
