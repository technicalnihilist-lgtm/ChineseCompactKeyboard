package com.example.chinesecompactkeyboard.input

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.chinesecompactkeyboard.core.KeyboardPalette

/**
 * Horizontal candidate strip. Fixed height, horizontally scrollable, rebuilt on
 * every composition change — cheap enough for ~30 items and it never changes
 * the height of the keyboard, so the input area never jitters.
 */
class CandidateBar(context: Context) : HorizontalScrollView(context) {

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6f).toInt(), 0, dp(6f).toInt(), 0)
    }
    private var palette: KeyboardPalette? = null
    private var onPick: ((String) -> Unit)? = null
    private var onMore: (() -> Unit)? = null

    init {
        isHorizontalScrollBarEnabled = false
        isFillViewport = true
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    fun applyTheme(p: KeyboardPalette) {
        palette = p
        setBackgroundColor(p.background)
        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i)
            if (child is TextView) child.setTextColor(p.candidateText)
        }
        invalidate()
    }

    fun setPicker(picker: (String) -> Unit) {
        onPick = picker
    }

    fun setOnMore(handler: () -> Unit) {
        onMore = handler
    }

    fun clear() {
        row.removeAllViews()
    }

    fun showHint(text: String) {
        val p = palette ?: return
        row.removeAllViews()
        row.addView(TextView(context).apply {
            this.text = text
            setTextColor(p.hint)
            textSize = 12f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4f).toInt(), 0, dp(4f).toInt(), 0)
            maxLines = 1
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT))
    }

    fun setCandidates(items: List<String>, highlightFirst: Boolean = true) {
        val p = palette ?: return
        row.removeAllViews()
        if (items.isEmpty()) return
        val shown = items.take(MAX_VISIBLE)
        shown.forEachIndexed { index, text ->
            val tv = TextView(context).apply {
                this.text = text
                setTextColor(if (index == 0 && highlightFirst) p.accent else p.candidateText)
                textSize = if (index == 0 && highlightFirst) 19f else 18f
                typeface = if (index == 0 && highlightFirst) Typeface.create("sans-serif-medium", Typeface.NORMAL)
                else Typeface.create("sans-serif", Typeface.NORMAL)
                gravity = Gravity.CENTER
                setPadding(dp(11f).toInt(), 0, dp(11f).toInt(), 0)
                isClickable = true
                setOnClickListener { onPick?.invoke(text) }
            }
            row.addView(
                tv,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        if (items.size > MAX_VISIBLE && onMore != null) {
            val more = TextView(context).apply {
                this.text = "›"
                setTextColor(p.hint)
                textSize = 20f
                gravity = Gravity.CENTER
                setPadding(dp(10f).toInt(), 0, dp(10f).toInt(), 0)
                setOnClickListener { onMore?.invoke() }
            }
            row.addView(
                more,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        scrollTo(0, 0)
    }

    /** Convenience for the "no candidates" state. */
    fun setEmptyState(message: String) {
        val p = palette ?: return
        row.removeAllViews()
        row.addView(TextView(context).apply {
            text = message
            setTextColor(p.hint)
            textSize = 12f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6f).toInt(), 0, dp(6f).toInt(), 0)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT))
    }

    fun itemCount(): Int = row.childCount

    fun firstItemText(): String? = (row.getChildAt(0) as? TextView)?.text?.toString()

    fun visibleItems(): List<String> = (0 until row.childCount).mapNotNull {
        (row.getChildAt(it) as? TextView)?.text?.toString()
    }

    private companion object {
        const val MAX_VISIBLE = 30
    }
}
