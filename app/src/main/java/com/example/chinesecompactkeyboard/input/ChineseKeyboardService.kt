package com.example.chinesecompactkeyboard.input

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import com.example.chinesecompactkeyboard.core.Prefs
import com.example.chinesecompactkeyboard.core.Theme
import com.example.chinesecompactkeyboard.data.ClipboardEntry
import com.example.chinesecompactkeyboard.data.ClipboardRepository
import com.example.chinesecompactkeyboard.engine.PinyinEngine
import com.example.chinesecompactkeyboard.engine.UserModel
import com.example.chinesecompactkeyboard.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The IME itself.
 *
 * Everything the keyboard inserts goes through a single [InputConnection];
 * pinyin composition uses setComposingText so the app underlines the raw pinyin
 * and replaces it atomically when a candidate is committed.
 */
class ChineseKeyboardService : InputMethodService(),
    KeyboardView.Listener, ClipboardPanelView.Callbacks {

    private lateinit var prefs: Prefs
    private lateinit var clipboard: ClipboardRepository
    private lateinit var userModel: UserModel

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var engine: PinyinEngine? = null
    @Volatile
    private var engineLoading = false

    private var root: LinearLayout? = null
    private var candidateBar: CandidateBar? = null
    private var keyboardView: KeyboardView? = null
    private var clipboardPanel: ClipboardPanelView? = null

    private val composing = StringBuilder()
    private var candidates: List<String> = emptyList()
    private var previousWord: String? = null

    private var layoutKind = KeyboardView.LayoutKind.LETTERS
    private var shifted = false
    private var capsLock = false
    private var showingClipboard = false
    private var clipboardReadable = true

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        mainHandler.post {
            if (key == "theme" || key == "keyboard_height" || key == "candidate_bar") {
                applyLayoutPreferences()
            }
            if (key == "key_scale") {
                keyboardView?.requestLayout()
                keyboardView?.invalidate()
            }
            if (key == "traditional" || key == "suggestions") {
                refreshCandidates()
            }
            if (key == "chinese_mode") {
                syncKeyboardState()
            }
            applyTheme()
        }
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        clipboard = ClipboardRepository(this)
        userModel = UserModel(this)
        userModel.ensureLoaded()
        prefs.registerListener(prefListener)
        loadEngineAsync()
    }

    private fun loadEngineAsync() {
        if (engine != null || engineLoading) return
        engineLoading = true
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    PinyinEngine.get(applicationContext)
                } catch (t: Throwable) {
                    null
                }
            }
            engine = loaded
            engineLoading = false
            if (composing.isNotEmpty()) refreshCandidates()
        }
    }

    override fun onCreateInputView(): View {
        val p = Theme.paletteFor(this)
        root = ImeRoot(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(p.background)
        }

        candidateBar = CandidateBar(this).apply {
            applyTheme(p)
            setPicker { text -> commitCandidate(text) }
        }
        root!!.addView(
            candidateBar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(CANDIDATE_BAR_DP))
        )

        keyboardView = KeyboardView(this).apply {
            listener = this@ChineseKeyboardService
            applyTheme(p)
        }
        root!!.addView(keyboardView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        clipboardPanel = ClipboardPanelView(this).apply {
            applyTheme(p)
            setCallbacks(this@ChineseKeyboardService)
            visibility = View.GONE
        }
        root!!.addView(clipboardPanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        applyLayoutPreferences()
        syncKeyboardState()
        showIdleHint()
        return root!!
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        /** Candidate strip height; kept tight so the keyboard stays compact. */
        const val CANDIDATE_BAR_DP = 32f
    }

    /**
     * The input view always measures itself at exactly the height chosen in
     * settings, whatever measure spec the window manager hands down. Without
     * this the weighted children would collapse to zero height on devices that
     * measure the IME with an unspecified spec.
     */
    private inner class ImeRoot(context: Context) : LinearLayout(context) {

        init {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setOnApplyWindowInsetsListener { view, insets ->
                    // Gesture navigation draws over the IME window on many devices;
                    // pad the bottom so the last key row is never clipped.
                    val bars = insets.getInsets(
                        WindowInsets.Type.navigationBars() or WindowInsets.Type.systemGestures()
                    )
                    if (view.paddingBottom != bars.bottom) {
                        view.setPadding(0, 0, 0, bars.bottom)
                        view.requestLayout()
                    }
                    insets
                }
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val mode = MeasureSpec.getMode(heightMeasureSpec)
            val available = MeasureSpec.getSize(heightMeasureSpec)
            val screenCap = (resources.displayMetrics.heightPixels * 0.45f).toInt()
            var height = dp(prefs.keyboardHeightDp.toFloat())
            height = minOf(height, screenCap)
            if (mode != MeasureSpec.UNSPECIFIED && available > 0) {
                height = minOf(height, available)
            }
            super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            )
        }
    }

    private fun applyLayoutPreferences() {
        val r = root ?: return
        r.requestLayout()
        candidateBar?.visibility = if (prefs.candidateBarVisible) View.VISIBLE else View.GONE
        val barParams = candidateBar?.layoutParams
        if (barParams != null && barParams.height != dp(CANDIDATE_BAR_DP)) {
            barParams.height = dp(CANDIDATE_BAR_DP)
            candidateBar?.layoutParams = barParams
        }
        keyboardView?.requestLayout()
        keyboardView?.invalidate()
    }

    private fun applyTheme() {
        val p = Theme.paletteFor(this)
        root?.setBackgroundColor(p.background)
        keyboardView?.applyTheme(p)
        candidateBar?.applyTheme(p)
        clipboardPanel?.applyTheme(p)
        if (showingClipboard) renderClipboard() else refreshCandidates()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        resetComposition()
        previousWord = null
        applyTheme()
        applyLayoutPreferences()
        syncKeyboardState()
        if (info != null && (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) {
            // Respect apps that ask for no personalised learning.
        }
        captureClipboard()
        showIdleHint()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        userModel.flush()
        currentInputConnection?.finishComposingText()
        resetComposition()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        userModel.flush()
        super.onFinishInput()
    }

    override fun onDestroy() {
        userModel.flush()
        prefs.unregisterListener(prefListener)
        keyboardView?.release()
        scope.cancel()
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    // ------------------------------------------------------------------- state

    private fun resetComposition() {
        composing.setLength(0)
        candidates = emptyList()
    }

    private fun syncKeyboardState() {
        keyboardView?.setLayout(layoutKind, prefs.chineseMode, shifted, capsLock)
    }

    private fun showIdleHint() {
        if (showingClipboard) return
        val bar = candidateBar ?: return
        if (!prefs.candidateBarVisible) return
        if (composing.isNotEmpty()) return
        val predictions = if (prefs.pinyinSuggestions && previousWord != null)
            userModel.predictions(previousWord, 8) else emptyList()
        if (predictions.isNotEmpty()) {
            candidates = predictions
            bar.setCandidates(predictions)
        } else if (engine == null) {
            bar.showHint("Loading dictionary…")
        } else {
            bar.showHint(if (prefs.chineseMode) "输入拼音 · type pinyin" else "English")
        }
    }

    private fun refreshCandidates() {
        val bar = candidateBar ?: return
        if (showingClipboard) return
        if (composing.isEmpty()) {
            showIdleHint()
            return
        }
        val e = engine
        if (e == null) {
            loadEngineAsync()
            bar.showHint("Loading dictionary…")
            return
        }
        val list = e.candidates(
            composing.toString(),
            prefs.traditional,
            previousWord,
            userModel,
            prefs.pinyinSuggestions
        )
        candidates = list
        if (list.isEmpty()) bar.showHint("No matches — tap space to insert pinyin")
        else bar.setCandidates(list)
    }

    private fun updateComposition() {
        val ic = currentInputConnection ?: return
        if (composing.isEmpty()) {
            ic.finishComposingText()
            refreshCandidates()
        } else {
            ic.setComposingText(composing, 1)
            refreshCandidates()
        }
    }

    // -------------------------------------------------------------------- keys

    override fun onKey(spec: KeySpec) {
        when (spec.kind) {
            KeyKind.CHAR -> handleCharacter(spec.code)
            KeyKind.SPACE -> handleSpace()
            KeyKind.ENTER -> handleEnter()
            KeyKind.BACKSPACE -> handleBackspace()
            KeyKind.SHIFT -> toggleShift()
            KeyKind.MODE -> toggleMode()
            KeyKind.LAYOUT -> toggleLayout()
            KeyKind.MENU -> toggleClipboard()
            KeyKind.FUNCTION -> Unit
        }
    }

    override fun onLongPressKey(spec: KeySpec) {
        when (spec.code) {
            "MENU" -> openSettings()
            "DEL" -> handleBackspace()
            "SPACE" -> {
                // Long-press space: switch input mode, a familiar shortcut.
                toggleMode()
            }
        }
    }

    override fun onKeyFeedback() {
        if (!prefs.soundEnabled) return
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.7f)
        } catch (_: Throwable) {
        }
    }

    private fun handleCharacter(raw: String) {
        if (raw.isEmpty()) return
        val chinese = prefs.chineseMode
        val isLetter = raw.length == 1 && raw[0].isLetter()

        if (chinese && isLetter && raw[0].code < 128) {
            composing.append(raw.lowercase())
            updateComposition()
            return
        }

        // Anything else (punctuation, digits, English letters) commits first.
        commitCompositionIfAny()
        val text = if (chinese) chinesePunctuation(raw) else raw
        currentInputConnection?.commitText(text, 1)
        afterDirectCommit(text)
        if (!chinese && isLetter) {
            if (capsLock) {
                // stay in caps
            } else if (shifted) {
                shifted = false
                syncKeyboardState()
            }
        }
    }

    private fun chinesePunctuation(raw: String): String = when (raw) {
        "," -> "，"
        "." -> "。"
        "?" -> "？"
        "!" -> "！"
        ":" -> "："
        ";" -> "；"
        "(" -> "（"
        ")" -> "）"
        "'" -> "’"
        "\"" -> "“"
        "@" -> "@"
        else -> raw
    }

    private fun handleSpace() {
        if (composing.isNotEmpty()) {
            val pick = candidates.firstOrNull()
            if (pick != null) commitCandidate(pick) else commitRawComposition()
            return
        }
        currentInputConnection?.commitText(" ", 1)
        afterDirectCommit(" ")
    }

    private fun handleEnter() {
        if (composing.isNotEmpty()) {
            val pick = candidates.firstOrNull()
            if (pick != null) commitCandidate(pick) else commitRawComposition()
        }
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED &&
            (info?.imeOptions?.and(EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0)
        ) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
        afterDirectCommit("\n")
    }

    private fun handleBackspace() {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) {
            composing.deleteCharAt(composing.length - 1)
            if (composing.isEmpty()) {
                ic.setComposingText("", 1)
                ic.finishComposingText()
                refreshCandidates()
            } else {
                ic.setComposingText(composing, 1)
                refreshCandidates()
            }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ic.deleteSurroundingTextInCodePoints(1, 0)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
    }

    private fun toggleShift() {
        if (capsLock) {
            capsLock = false
            shifted = false
        } else if (shifted) {
            capsLock = true
        } else {
            shifted = true
        }
        syncKeyboardState()
    }

    private fun toggleMode() {
        if (composing.isNotEmpty()) commitRawComposition()
        prefs.chineseMode = !prefs.chineseMode
        shifted = false
        capsLock = false
        syncKeyboardState()
        showIdleHint()
    }

    private fun toggleLayout() {
        layoutKind = if (layoutKind == KeyboardView.LayoutKind.LETTERS)
            KeyboardView.LayoutKind.SYMBOLS else KeyboardView.LayoutKind.LETTERS
        syncKeyboardState()
    }

    private fun afterDirectCommit(text: String) {
        if (text.isNotBlank()) {
            previousWord = null
            userModel.flush()
        }
        if (text == "." || text == "。" || text == "！" || text == "？" || text == "?" || text == "!") {
            shifted = true
            capsLock = false
            syncKeyboardState()
        }
    }

    // -------------------------------------------------------------- committing

    private fun commitCandidate(text: String) {
        val ic = currentInputConnection ?: return
        val hadComposition = composing.isNotEmpty()
        composing.setLength(0)
        ic.commitText(text, 1)
        candidates = emptyList()
        if (text.isNotEmpty() && !text[0].isAsciiPunctuation()) {
            userModel.recordCommit(text, previousWord)
            previousWord = text
        }
        if (!hadComposition) ic.finishComposingText()
        refreshCandidates()
    }

    private fun Char.isAsciiPunctuation(): Boolean = this in ".,!?;:'\"()[]{}-_/\\@#\$%^&*+=<>~`|"

    private fun commitCompositionIfAny() {
        if (composing.isEmpty()) return
        val pick = candidates.firstOrNull()
        if (pick != null) commitCandidate(pick) else commitRawComposition()
    }

    private fun commitRawComposition() {
        val ic = currentInputConnection ?: return
        val raw = composing.toString()
        composing.setLength(0)
        candidates = emptyList()
        if (raw.isNotEmpty()) ic.commitText(raw, 1) else ic.finishComposingText()
        refreshCandidates()
    }

    // -------------------------------------------------------------- clipboard

    private fun captureClipboard() {
        clipboardReadable = true
        clipboard.captureFromSystem()
        clipboardReadable = clipboard.readClipboard() != null || clipboard.cached().isNotEmpty()
    }

    private fun toggleClipboard() {
        showingClipboard = !showingClipboard
        if (showingClipboard) {
            keyboardView?.visibility = View.GONE
            clipboardPanel?.visibility = View.VISIBLE
            captureClipboard()
            renderClipboard()
        } else {
            closeClipboard()
        }
    }

    private fun closeClipboard() {
        showingClipboard = false
        clipboardPanel?.visibility = View.GONE
        keyboardView?.visibility = View.VISIBLE
        showIdleHint()
    }

    private fun renderClipboard() {
        val panel = clipboardPanel ?: return
        scope.launch {
            val items = withContext(Dispatchers.IO) { clipboard.refresh() }
            panel.render(items, clipboardReadable)
        }
    }

    override fun onPaste(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        previousWord = null
        closeClipboard()
    }

    override fun onCopy(text: String) {
        clipboard.copy(text)
    }

    override fun onDelete(entry: ClipboardEntry) {
        clipboard.delete(entry.id)
        renderClipboard()
    }

    override fun onClearAll() {
        clipboard.clear()
        renderClipboard()
    }

    override fun onClose() {
        closeClipboard()
    }

    private fun openSettings() {
        try {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Throwable) {
        }
    }

    // ------------------------------------------------------------- selections

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (composing.isNotEmpty() && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            if (newSelStart < candidatesStart || newSelStart > candidatesEnd) {
                composing.setLength(0)
                candidates = emptyList()
                showIdleHint()
            }
        }
    }

    override fun onEvaluateInputViewShown(): Boolean = super.onEvaluateInputViewShown()
}
