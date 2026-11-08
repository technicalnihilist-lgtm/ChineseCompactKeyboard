package com.example.chinesecompactkeyboard.engine

import android.content.Context
import com.example.chinesecompactkeyboard.data.AppDatabase
import com.example.chinesecompactkeyboard.data.UserBigram
import com.example.chinesecompactkeyboard.data.UserWord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Remembers which words the user picks and which words follow which, so the
 * candidate list adapts to the way that person actually types.
 *
 * Everything is kept in small in-memory maps for zero-latency lookups during
 * typing and written to Room in the background.
 */
class UserModel(context: Context) {

    private val appContext = context.applicationContext
    private val dao = AppDatabase.get(appContext).learningDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val wordFreq = HashMap<String, Int>(512)
    private val bigrams = HashMap<String, HashMap<String, Int>>(256)
    private val dirtyWords = HashMap<String, Int>()
    private val dirtyBigrams = HashMap<String, Int>()

    @Volatile
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        scope.launch {
            val words = withContext(Dispatchers.IO) { dao.allWords() }
            val big = withContext(Dispatchers.IO) { dao.topBigrams(MAX_BIGRAMS) }
            synchronized(wordFreq) {
                for (w in words) wordFreq[w.word] = w.freq
            }
            synchronized(bigrams) {
                for (b in big) {
                    bigrams.getOrPut(b.prev) { HashMap(8) }[b.next] = b.freq
                }
            }
        }
    }

    fun frequency(word: String): Int = synchronized(wordFreq) { wordFreq[word] ?: 0 }

    fun bigramFrequency(prev: String?, next: String): Int {
        if (prev.isNullOrEmpty()) return 0
        return synchronized(bigrams) { bigrams[prev]?.get(next) ?: 0 }
    }

    fun recordCommit(word: String, prevWord: String?) {
        if (word.isEmpty()) return
        val clean = word.trim()
        if (clean.isEmpty()) return
        synchronized(wordFreq) {
            val next = (wordFreq[clean] ?: 0) + 1
            wordFreq[clean] = next
            dirtyWords[clean] = next
        }
        if (!prevWord.isNullOrEmpty() && prevWord.length <= 8 && clean.length <= 8) {
            synchronized(bigrams) {
                val map = bigrams.getOrPut(prevWord) { HashMap(8) }
                val next = (map[clean] ?: 0) + 1
                map[clean] = next
                dirtyBigrams["$prevWord\u0000$clean"] = next
            }
        }
        if (dirtyWords.size >= 12 || dirtyBigrams.size >= 12) flush()
    }

    fun flush() {
        var words: List<UserWord> = emptyList()
        var big: List<UserBigram> = emptyList()
        val now = System.currentTimeMillis()
        synchronized(wordFreq) {
            if (dirtyWords.isNotEmpty()) {
                words = dirtyWords.map { (w, f) -> UserWord(w, f, now) }
                dirtyWords.clear()
            }
        }
        synchronized(bigrams) {
            if (dirtyBigrams.isNotEmpty()) {
                big = dirtyBigrams.map { (k, f) ->
                    val parts = k.split("\u0000")
                    UserBigram(parts[0], parts[1], f, now)
                }
                dirtyBigrams.clear()
            }
        }
        if (words.isEmpty() && big.isEmpty()) return
        scope.launch {
            dao.upsertWords(words)
            dao.upsertBigrams(big)
        }
    }

    /** Most likely next words after [prev]. */
    fun predictions(prev: String?, limit: Int): List<String> {
        if (prev.isNullOrEmpty()) return emptyList()
        val map = synchronized(bigrams) { bigrams[prev] } ?: return emptyList()
        return map.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }

    fun clearLearning() {
        synchronized(wordFreq) {
            wordFreq.clear()
            dirtyWords.clear()
        }
        synchronized(bigrams) {
            bigrams.clear()
            dirtyBigrams.clear()
        }
        scope.launch {
            dao.clearWords()
            dao.clearBigrams()
        }
    }

    private companion object {
        const val MAX_BIGRAMS = 20000
    }
}
