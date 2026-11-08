package com.example.chinesecompactkeyboard

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.example.chinesecompactkeyboard.core.Prefs
import com.example.chinesecompactkeyboard.input.ChineseKeyboardService
import com.example.chinesecompactkeyboard.ui.ClipboardActivity
import com.example.chinesecompactkeyboard.ui.MainActivity
import com.example.chinesecompactkeyboard.ui.TestActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Launches every screen and the IME service on the JVM Android runtime. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppSmokeTest {

    @Test
    fun mainActivityInflates() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertNotNull(activity.findViewById(R.id.status_text))
        assertNotNull(activity.findViewById(R.id.slider_height))
        assertNotNull(activity.findViewById(R.id.theme_group))
    }

    @Test
    fun clipboardActivityInflates() {
        val activity = Robolectric.buildActivity(ClipboardActivity::class.java).setup().get()
        assertNotNull(activity.findViewById(R.id.recycler))
    }

    @Test
    fun testActivityInflates() {
        val activity = Robolectric.buildActivity(TestActivity::class.java).setup().get()
        assertNotNull(activity.findViewById(R.id.test_input))
    }

    @Test
    fun imeServiceBuildsInputView() {
        val service = Robolectric.buildService(ChineseKeyboardService::class.java).create().get()
        val view = service.onCreateInputView()
        assertNotNull(view)
        assertTrue(view.height >= 0)
    }

    @Test
    fun inputViewMeasuresToTheConfiguredHeight() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Prefs.get(context).keyboardHeightDp = 240
        val service = Robolectric.buildService(ChineseKeyboardService::class.java).create().get()
        val view = service.onCreateInputView()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val wanted = (240 * context.resources.displayMetrics.density).toInt()
        val cap = (context.resources.displayMetrics.heightPixels * 0.45f).toInt()
        assertEquals(minOf(wanted, cap), view.measuredHeight)
    }

}
