package com.example.chinesecompactkeyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import com.example.chinesecompactkeyboard.core.Prefs
import com.example.chinesecompactkeyboard.core.Theme
import com.example.chinesecompactkeyboard.engine.PinyinEngine
import com.example.chinesecompactkeyboard.input.CandidateBar
import com.example.chinesecompactkeyboard.input.ChineseKeyboardService
import com.example.chinesecompactkeyboard.ui.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the real views to PNG files under docs/screenshots.
 * Run with: ./gradlew testDebugUnitTest --tests '*ScreenshotTest*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    private val outputDir = File("../docs/screenshots")

    private fun save(view: View, width: Int, height: Int, name: String) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFFFFFFF.toInt())
        view.draw(canvas)
        outputDir.mkdirs()
        FileOutputStream(File(outputDir, name)).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        println("wrote ${File(outputDir, name).absolutePath}")
    }

    private fun findCandidateBar(view: View): CandidateBar? {
        if (view is CandidateBar) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findCandidateBar(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun renderKeyboard(dark: Boolean, name: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = Prefs.get(context)
        prefs.theme = if (dark) Prefs.THEME_DARK else Prefs.THEME_LIGHT
        prefs.keyboardHeightDp = Prefs.DEFAULT_HEIGHT_DP
        prefs.chineseMode = true

        val service = Robolectric.buildService(ChineseKeyboardService::class.java).create().get()
        val inputView = service.onCreateInputView()

        val bar = findCandidateBar(inputView)
        val engine = PinyinEngine.get(context)
        bar?.applyTheme(Theme.palette(dark))
        bar?.setCandidates(engine.candidates("nihao", false, null, null, true, 12))

        val width = 1080
        val height = (prefs.keyboardHeightDp * context.resources.displayMetrics.density).toInt()
        save(inputView, width, height, name)
    }

    @Test
    fun renderLightKeyboard() = renderKeyboard(dark = false, name = "keyboard-light.png")

    @Test
    fun renderDarkKeyboard() = renderKeyboard(dark = true, name = "keyboard-dark.png")

    @Test
    fun renderSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Prefs.get(context).theme = Prefs.THEME_LIGHT
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val decor = activity.window.decorView
        save(decor, 1080, 2100, "settings.png")
    }
}
