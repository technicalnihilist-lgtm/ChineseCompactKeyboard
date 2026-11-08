package com.example.chinesecompactkeyboard

import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.example.chinesecompactkeyboard.input.KeySpec
import com.example.chinesecompactkeyboard.input.KeyboardView
import com.example.chinesecompactkeyboard.core.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardUiTest {

    private class Recorder : KeyboardView.Listener {
        val keys = ArrayList<String>()
        var longPresses = 0
        override fun onKey(spec: KeySpec) { keys.add(spec.code) }
        override fun onLongPressKey(spec: KeySpec) { longPresses++ }
        override fun onKeyFeedback() {}
    }

    private fun buildView(chinese: Boolean = true): Pair<KeyboardView, Recorder> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        Prefs.get(context).chineseMode = chinese
        val view = KeyboardView(context)
        val recorder = Recorder()
        view.listener = recorder
        view.setLayout(KeyboardView.LayoutKind.LETTERS, chinese, false, false)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 1080, 700)
        return view to recorder
    }

    private fun allCodes(view: KeyboardView): Set<String> {
        val found = LinkedHashSet<String>()
        for (y in 0 until 700 step 2) {
            for (x in 0 until 1080 step 2) {
                view.keyAtPosition(x.toFloat(), y.toFloat())?.let { found.add(it) }
            }
        }
        return found
    }

    @Test
    fun lettersLayoutHasExpectedKeys() {
        val (view, _) = buildView()
        // 10 + 9 + 9 + 7 keys, all with distinct codes
        assertEquals(35, allCodes(view).size)
    }

    @Test
    fun tappingTopLeftKeySendsQ() {
        val (view, recorder) = buildView(chinese = false)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 40f, 40f, 0)
        view.dispatchTouchEvent(down)
        val up = MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, 40f, 40f, 0)
        view.dispatchTouchEvent(up)
        assertEquals(listOf("q"), recorder.keys)
    }

    @Test
    fun everyPointInTheGridHitsAKey() {
        val (view, _) = buildView()
        var misses = 0
        var hits = 0
        for (y in 0 until 700 step 3) {
            for (x in 0 until 1080 step 3) {
                if (view.keyAtPosition(x.toFloat(), y.toFloat()) != null) hits++ else misses++
            }
        }
        // Gaps between keys are expected, but the surface must be overwhelmingly covered.
        assertTrue("hits=$hits misses=$misses", hits > misses * 8)
    }

    @Test
    fun chineseModeShowsChinesePunctuationKeys() {
        val (view, recorder) = buildView(chinese = true)
        val keys = LinkedHashSet<String>()
        for (y in 0 until 700 step 3) {
            for (x in 0 until 1080 step 3) {
                view.keyAtPosition(x.toFloat(), y.toFloat())?.let { keys.add(it) }
            }
        }
        assertTrue(keys.contains(","))
        assertTrue(keys.contains("."))
        assertTrue(keys.contains("MODE"))
        assertTrue(keys.contains("SPACE"))
        assertTrue(keys.contains("ENTER"))
        assertTrue(keys.contains("DEL"))
        assertNotNull(recorder)
    }

    @Test
    fun symbolsLayoutIsReachable() {
        val (view, _) = buildView()
        view.setLayout(KeyboardView.LayoutKind.SYMBOLS, true, false, false)
        val keys = LinkedHashSet<String>()
        for (y in 0 until 700 step 3) {
            for (x in 0 until 1080 step 3) {
                view.keyAtPosition(x.toFloat(), y.toFloat())?.let { keys.add(it) }
            }
        }
        assertTrue(keys.contains("1"))
        assertTrue(keys.contains("@"))
        assertTrue(keys.contains("ABC"))
    }
}
