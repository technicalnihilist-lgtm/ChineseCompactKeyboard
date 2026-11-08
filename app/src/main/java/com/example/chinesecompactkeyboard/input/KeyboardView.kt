package com.example.chinesecompactkeyboard.input

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.example.chinesecompactkeyboard.core.KeyboardPalette
import com.example.chinesecompactkeyboard.core.Prefs
import com.example.chinesecompactkeyboard.core.Theme
import kotlin.math.min

/**
 * A single canvas-drawn view for the whole keyboard.
 *
 * Drawing every key in one view (instead of ~40 child views) keeps layout flat,
 * startup fast and scrolling/jitter free on low-end devices. Key events fire on
 * touch-down for the lowest possible typing latency; the press animation is
 * driven by the draw loop itself.
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onKey(spec: KeySpec)
        fun onLongPressKey(spec: KeySpec)
        fun onKeyFeedback()
    }

    var listener: Listener? = null

    private val prefs = Prefs.get(context)
    private var palette: KeyboardPalette = Theme.paletteFor(context)

    private var layoutKind = LayoutKind.LETTERS
    private var chinese = true
    private var shift = false
    private var capsLock = false

    enum class LayoutKind { LETTERS, SYMBOLS }

    private class PlacedKey(val spec: KeySpec, val rect: RectF)

    private val placed = ArrayList<PlacedKey>(48)
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val popupPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    private var pressedIndex = -1
    private var pressStart = 0L
    private var releasedIndex = -1
    private var releaseStart = 0L
    private var animating = false

    private val handler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null

    private val interpolator = DecelerateInterpolator()
    private var keyScale = 1f

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    init {
        isClickable = true
        isFocusable = false
        setBackgroundColor(palette.background)
    }

    fun applyTheme(p: KeyboardPalette) {
        palette = p
        keyScale = prefs.keyScalePercent / 100f
        setBackgroundColor(p.background)
        invalidate()
    }

    fun setLayout(kind: LayoutKind, chinese: Boolean, shift: Boolean, capsLock: Boolean) {
        val changed = layoutKind != kind || this.chinese != chinese || this.shift != shift
        layoutKind = kind
        this.chinese = chinese
        this.shift = shift
        this.capsLock = capsLock
        if (changed) {
            requestLayout()
            rebuild()
        }
        invalidate()
    }

    private fun currentRows(): List<KeyRow> = when (layoutKind) {
        LayoutKind.LETTERS -> KeyLayouts.letters(chinese, shift)
        LayoutKind.SYMBOLS -> KeyLayouts.symbols(chinese)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    private fun rebuild() {
        placed.clear()
        if (width == 0 || height == 0) return
        val rows = currentRows()
        val gap = dp(2.0f)
        val horizontalPadding = dp(2f)
        val verticalPadding = dp(2.0f)
        val usableWidth = width - horizontalPadding * 2
        val usableHeight = height - verticalPadding * 2
        val unit = usableWidth / 10f
        val rowHeight = usableHeight / rows.size
        keyScale = prefs.keyScalePercent / 100f

        var y = verticalPadding
        for (row in rows) {
            val rowWidth = row.keys.sumOf { it.weight.toDouble() }.toFloat() * unit
            var x = horizontalPadding + (usableWidth - rowWidth) / 2f
            for (spec in row.keys) {
                val keyWidth = spec.weight * unit
                val rect = RectF(
                    x + gap / 2f,
                    y + gap / 2f,
                    x + keyWidth - gap / 2f,
                    y + rowHeight - gap / 2f
                )
                placed.add(PlacedKey(spec, rect))
                x += keyWidth
            }
            y += rowHeight
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        var needsAnimation = false
        val radius = dp(5.5f) * (0.9f + 0.1f * keyScale)
        textPaint.textSize = dp(17.5f) * keyScale
        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)

        for (index in placed.indices) {
            val key = placed[index]
            var scale = 1f
            if (index == pressedIndex) {
                val t = min(1f, (now - pressStart) / 70f)
                scale = 1f - 0.055f * interpolator.getInterpolation(t)
                if (t < 1f) needsAnimation = true
            } else if (index == releasedIndex) {
                val t = min(1f, (now - releaseStart) / 90f)
                scale = 1f - 0.055f * (1f - interpolator.getInterpolation(t))
                if (t < 1f) needsAnimation = true
            }

            val isFunction = key.spec.kind != KeyKind.CHAR
            val baseColor = if (isFunction) palette.functionKeyBackground else palette.keyBackground
            val activeColor = if (isFunction) palette.functionKeyPressed else palette.keyPressed
            val color = when {
                index == pressedIndex -> activeColor
                index == releasedIndex -> blend(activeColor, baseColor, min(1f, (now - releaseStart) / 90f))
                else -> baseColor
            }

            val rect = key.rect
            val cx = rect.centerX()
            val cy = rect.centerY()
            val halfW = rect.width() / 2f * scale
            val halfH = rect.height() / 2f * scale
            val drawRect = RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)

            keyPaint.color = color
            canvas.drawRoundRect(drawRect, radius, radius, keyPaint)

            val isActive = when (key.spec.kind) {
                KeyKind.SHIFT -> shift
                KeyKind.MODE -> chinese
                else -> false
            }
            textPaint.color = when {
                isActive -> palette.accent
                isFunction -> palette.functionKeyText
                else -> palette.keyText
            }
            val label = key.spec.label
            val size = when (key.spec.kind) {
                KeyKind.CHAR -> dp(17.5f) * keyScale
                KeyKind.SPACE -> dp(11.5f) * keyScale
                else -> dp(12f) * keyScale
            }
            textPaint.textSize = size
            val metrics = textPaint.fontMetrics
            val textY = cy - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(label, cx, textY, textPaint)
        }

        if (pressedIndex >= 0 && placed[pressedIndex].spec.kind == KeyKind.CHAR) {
            drawPreview(canvas, placed[pressedIndex], now)
            if (now - pressStart < 120) needsAnimation = true
        }

        if (needsAnimation) postInvalidateOnAnimation()
    }

    private fun drawPreview(canvas: Canvas, key: PlacedKey, now: Long) {
        val t = min(1f, (now - pressStart) / 80f)
        if (t <= 0f) return
        val label = key.spec.label
        val size = dp(24f) * keyScale
        textPaint.textSize = size
        textPaint.color = palette.popupText
        val textWidth = textPaint.measureText(label)
        val padH = dp(12f)
        val padV = dp(8f)
        val boxW = textWidth + padH * 2
        val boxH = size + padV * 2
        var cx = key.rect.centerX()
        val half = boxW / 2
        if (cx - half < dp(2f)) cx = dp(2f) + half
        if (cx + half > width - dp(2f)) cx = width - dp(2f) - half
        val top = key.rect.top - boxH - dp(4f)
        val box = RectF(cx - half, top, cx + half, top + boxH)
        popupPaint.color = palette.popupBackground
        popupPaint.alpha = (255 * t).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(box, dp(7f), dp(7f), popupPaint)
        textPaint.alpha = (255 * t).toInt().coerceIn(0, 255)
        val metrics = textPaint.fontMetrics
        canvas.drawText(label, cx, box.centerY() - (metrics.ascent + metrics.descent) / 2f, textPaint)
        textPaint.alpha = 255
        popupPaint.alpha = 255
    }

    private fun blend(from: Int, to: Int, t: Float): Int {
        val r = ((from shr 16 and 0xFF) + ((to shr 16 and 0xFF) - (from shr 16 and 0xFF)) * t).toInt()
        val g = ((from shr 8 and 0xFF) + ((to shr 8 and 0xFF) - (from shr 8 and 0xFF)) * t).toInt()
        val b = ((from and 0xFF) + ((to and 0xFF) - (from and 0xFF)) * t).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun keyAt(x: Float, y: Float): Int {
        for (index in placed.indices) {
            val r = placed[index].rect
            if (x >= r.left && x <= r.right && y >= r.top && y <= r.bottom) return index
        }
        return -1
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val index = keyAt(event.x, event.y)
                if (index < 0) return false
                pressedIndex = index
                pressStart = SystemClock.uptimeMillis()
                releasedIndex = -1
                feedback()
                dispatchKey(placed[index].spec)
                startRepeatIfNeeded(placed[index].spec)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (pressedIndex < 0) return false
                val index = keyAt(event.x, event.y)
                if (index != pressedIndex) {
                    cancelRepeat()
                    releasedIndex = pressedIndex
                    releaseStart = SystemClock.uptimeMillis()
                    pressedIndex = -1
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelRepeat()
                if (pressedIndex >= 0) {
                    releasedIndex = pressedIndex
                    releaseStart = SystemClock.uptimeMillis()
                    pressedIndex = -1
                }
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun dispatchKey(spec: KeySpec) {
        listener?.onKey(spec)
    }

    private fun startRepeatIfNeeded(spec: KeySpec) {
        when (spec.kind) {
            KeyKind.BACKSPACE -> {
                val runnable = object : Runnable {
                    override fun run() {
                        listener?.onLongPressKey(spec)
                        handler.postDelayed(this, 55)
                    }
                }
                repeatRunnable = runnable
                handler.postDelayed(runnable, 420)
            }

            KeyKind.MENU -> {
                val runnable = Runnable { listener?.onLongPressKey(spec) }
                repeatRunnable = runnable
                handler.postDelayed(runnable, 480)
            }

            KeyKind.SPACE -> {
                val runnable = Runnable { listener?.onLongPressKey(spec) }
                repeatRunnable = runnable
                handler.postDelayed(runnable, 520)
            }

            else -> Unit
        }
    }

    private fun cancelRepeat() {
        repeatRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable = null
    }

    private fun feedback() {
        if (prefs.vibrationEnabled) {
            try {
                val ms = prefs.vibrationMs.toLong()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(ms)
                }
            } catch (_: Throwable) {
            }
        }
        listener?.onKeyFeedback()
    }

    fun release() {
        cancelRepeat()
        handler.removeCallbacksAndMessages(null)
    }

    /** Debug helper: the code of the key under a point, if any. */
    fun keyAtPosition(x: Float, y: Float): String? {
        val index = keyAt(x, y)
        return if (index >= 0) placed[index].spec.code else null
    }
}
