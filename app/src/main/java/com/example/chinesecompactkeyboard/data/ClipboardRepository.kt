package com.example.chinesecompactkeyboard.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.example.chinesecompactkeyboard.core.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Clipboard history backed by Room.
 *
 * Android 10+ only lets the focused app read the clipboard, so an IME can read
 * it while its input view is visible — never in the background. Every time the
 * keyboard is shown (and whenever the clipboard panel is opened) the current
 * primary clip is read and, if it is new, stored. Copy events that happen while
 * the keyboard is closed are picked up the next time it opens.
 */
class ClipboardRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = AppDatabase.get(appContext).clipboardDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = Prefs.get(appContext)

    @Volatile
    private var cache: List<ClipboardEntry> = emptyList()

    fun cached(): List<ClipboardEntry> = cache

    /** Re-reads the list from the database and updates the cache. */
    suspend fun refresh(): List<ClipboardEntry> = withContext(Dispatchers.IO) {
        dao.all().also { cache = it }
    }

    fun refreshAsync(onDone: (List<ClipboardEntry>) -> Unit = {}) {
        scope.launch {
            val items = refresh()
            withContext(Dispatchers.Main) { onDone(items) }
        }
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) { dao.count() }

    /** Stores [text] if it is not already the newest entry. Returns true when stored. */
    fun add(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (cache.firstOrNull()?.text == trimmed) return false
        cache = listOf(ClipboardEntry(text = trimmed, createdAt = System.currentTimeMillis())) +
                cache.filter { it.text != trimmed }
        scope.launch {
            val existing = dao.newest()
            if (existing?.text != trimmed) {
                dao.insert(ClipboardEntry(text = trimmed, createdAt = System.currentTimeMillis()))
                dao.trimTo(Prefs.CLIPBOARD_LIMIT)
            }
            refresh()
        }
        return true
    }

    fun delete(id: Long) {
        cache = cache.filterNot { it.id == id }
        scope.launch {
            dao.delete(id)
            refresh()
        }
    }

    fun clear() {
        cache = emptyList()
        scope.launch {
            dao.clear()
            refresh()
        }
    }

    fun copy(text: String) {
        val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("keyboard", text))
    }

    /** Reads the current primary clip, if the platform allows it right now. */
    fun readClipboard(): String? {
        return try {
            val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (!cm.hasPrimaryClip()) return null
            val clip = cm.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            val item = clip.getItemAt(0)
            val text = item.coerceToText(appContext)?.toString()
            if (text.isNullOrBlank()) null else text
        } catch (t: Throwable) {
            // Some OEMs throw SecurityException when the clipboard is restricted.
            null
        }
    }

    /** Reads the clipboard and stores it when capture is enabled. */
    fun captureFromSystem() {
        if (!prefs.clipboardCapture) return
        val text = readClipboard() ?: return
        add(text)
    }

    fun isClipboardRestricted(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && readClipboard() == null
}
