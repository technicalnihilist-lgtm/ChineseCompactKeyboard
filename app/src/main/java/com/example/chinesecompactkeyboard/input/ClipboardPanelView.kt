package com.example.chinesecompactkeyboard.input

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.chinesecompactkeyboard.core.KeyboardPalette
import com.example.chinesecompactkeyboard.data.ClipboardEntry

/**
 * Clipboard history panel shown inside the keyboard. Tap an entry to paste it,
 * use the two small buttons to copy it back to the system clipboard or to
 * delete just that entry.
 */
class ClipboardPanelView(context: Context) : LinearLayout(context) {

    interface Callbacks {
        fun onPaste(text: String)
        fun onCopy(text: String)
        fun onDelete(entry: ClipboardEntry)
        fun onClearAll()
        fun onClose()
    }

    private var palette: KeyboardPalette? = null
    private val header = LinearLayout(context)
    private val listContainer = LinearLayout(context)
    private val scroll = ScrollView(context)
    private val note = TextView(context)
    private var callbacks: Callbacks? = null

    init {
        orientation = VERTICAL
        header.orientation = HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, dp(36f).toInt()))
        scroll.isVerticalScrollBarEnabled = true
        listContainer.orientation = VERTICAL
        scroll.addView(listContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        note.textSize = 11f
        note.maxLines = 2
        note.setPadding(dp(12f).toInt(), dp(4f).toInt(), dp(12f).toInt(), dp(4f).toInt())
        addView(note, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    fun setCallbacks(cb: Callbacks) {
        callbacks = cb
    }

    fun applyTheme(p: KeyboardPalette) {
        palette = p
        setBackgroundColor(p.panelBackground)
        note.setTextColor(p.hint)
        rebuildHeader()
    }

    private fun pill(color: Int, radiusDp: Float = 8f): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp)
    }

    private fun rebuildHeader() {
        val p = palette ?: return
        header.removeAllViews()
        header.setBackgroundColor(p.panelBackground)
        header.addView(TextView(context).apply {
            text = "Clipboard history"
            setTextColor(p.candidateText)
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(12f).toInt(), 0, dp(8f).toInt(), 0)
            gravity = Gravity.CENTER_VERTICAL
        }, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))

        header.addView(TextView(context).apply {
            text = "Clear all"
            setTextColor(p.accent)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(10f).toInt(), 0, dp(10f).toInt(), 0)
            setOnClickListener { callbacks?.onClearAll() }
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))

        header.addView(TextView(context).apply {
            text = "✕"
            setTextColor(p.hint)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(dp(12f).toInt(), 0, dp(12f).toInt(), 0)
            setOnClickListener { callbacks?.onClose() }
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    fun render(entries: List<ClipboardEntry>, clipboardReadable: Boolean) {
        val p = palette ?: return
        listContainer.removeAllViews()
        note.text = if (clipboardReadable)
            "Items are captured when the keyboard opens. Android blocks background clipboard access, so copies made while the keyboard is closed appear the next time it opens."
        else
            "Clipboard access is currently restricted by the system, so new items cannot be read right now."

        if (entries.isEmpty()) {
            listContainer.addView(TextView(context).apply {
                text = "No saved items yet. Copy some text, then reopen the keyboard."
                setTextColor(p.hint)
                textSize = 12.5f
                setPadding(dp(14f).toInt(), dp(18f).toInt(), dp(14f).toInt(), dp(18f).toInt())
            })
            return
        }

        entries.forEach { entry ->
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = pill(p.panelItem, 9f)
                setPadding(dp(12f).toInt(), dp(8f).toInt(), dp(6f).toInt(), dp(8f).toInt())
                isClickable = true
                setOnClickListener { callbacks?.onPaste(entry.text) }
            }
            val text = TextView(context).apply {
                this.text = entry.text
                setTextColor(p.candidateText)
                textSize = 13.5f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, 0, dp(8f).toInt(), 0)
            }
            row.addView(text, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

            row.addView(actionButton("Copy", p) { callbacks?.onCopy(entry.text) })
            row.addView(actionButton("✕", p) { callbacks?.onDelete(entry) })

            val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            params.setMargins(dp(8f).toInt(), dp(3f).toInt(), dp(8f).toInt(), dp(3f).toInt())
            listContainer.addView(row, params)
        }
    }

    private fun actionButton(label: String, p: KeyboardPalette, action: () -> Unit): View =
        TextView(context).apply {
            text = label
            setTextColor(p.accent)
            textSize = 11.5f
            gravity = Gravity.CENTER
            setPadding(dp(9f).toInt(), dp(5f).toInt(), dp(9f).toInt(), dp(5f).toInt())
            background = pill(if (p.dark) 0xFF2A2E34.toInt() else 0xFFEDEFF2.toInt(), 6f)
            setOnClickListener { action() }
        }

    fun entryRows(): Int = listContainer.childCount
}
