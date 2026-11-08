package com.example.chinesecompactkeyboard.engine

import android.content.Context
import kotlin.math.ln

/**
 * The offline pinyin engine.
 *
 * Pipeline for one keystroke string ("nihao"):
 *   1. segment into syllables               -> [ni, hao]
 *   2. exact key lookup                      -> 你好
 *   3. prefix lookup for longer phrases      -> 你好吗, 你好啊 ...
 *   4. sentence composition when no word covers everything -> 你 好 ...
 *   5. re-rank with the user's own word and bigram statistics
 *   6. convert to Traditional when that mode is on
 */
class PinyinEngine internal constructor(
    private val dictionary: AssetDictionary,
    private val converter: TraditionalConverter,
    private val segmenter: PinyinSegmenter
) {

    private val resultCache = object : LinkedHashMap<String, List<String>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean =
            size > 96
    }

    val syllableCount: Int get() = dictionary.syllables.size
    val wordCount: Int get() = dictionary.count

    /** Pinyin candidates for the current composition. */
    fun candidates(
        input: String,
        traditional: Boolean,
        previousWord: String?,
        userModel: UserModel?,
        suggestions: Boolean,
        limit: Int = 80
    ): List<String> {
        val query = normalize(input)
        if (query.isEmpty()) return emptyList()
        val cacheKey = "$query|$traditional|${previousWord.orEmpty()}"
        resultCache[cacheKey]?.let { return it }

        val scores = HashMap<String, Int>(96)
        // Order segmentations by dictionary evidence, so "keneng" tries
        // ke+neng (可能) before ken+eng.
        val segmentations = segmenter.segment(query)
            .map { seg ->
                val evidence = if (seg.isPartial)
                    dictionary.prefix(seg.query, 1, 200).firstOrNull()?.weight ?: 0
                else
                    dictionary.exact(seg.query, 1).firstOrNull()?.weight ?: 0
                seg to evidence
            }
            .sortedByDescending { it.second }
            .map { it.first }
        val primary = segmentations.firstOrNull()

        for ((index, seg) in segmentations.withIndex()) {
            val penalty = index * 200_000
            if (seg.isPartial) {
                val prefix = seg.query
                if (prefix.isEmpty()) continue
                for (entry in dictionary.prefix(prefix, 24, 3000)) {
                    bump(scores, entry.word, entry.weight + 600_000 - penalty)
                }
            } else {
                val key = seg.query
                if (key.isEmpty()) continue
                // Exact whole-word match: 你好 for "nihao".
                for (entry in dictionary.exact(key, 16)) {
                    bump(scores, entry.word, entry.weight + 3_000_000 - penalty)
                }
                // Longer phrases that start with exactly this key: 你好吗.
                for (entry in dictionary.prefix("$key ", 24, 3000)) {
                    val extraChars = entry.word.length - seg.syllables.size
                    val lengthPenalty = (if (extraChars > 0) extraChars else 0) * 12_000
                    bump(scores, entry.word, entry.weight + 900_000 - penalty - lengthPenalty)
                }
            }
        }

        if (suggestions && primary != null && !primary.isPartial && primary.syllables.size >= 2) {
            composeSentences(primary.syllables, 3).forEachIndexed { rank, sentence ->
                if (!scores.containsKey(sentence)) {
                    // Keep the DP order: the best composition must stay first.
                    bump(scores, sentence, 400_000 - rank * 1_000)
                }
            }
        }

        if (userModel != null && scores.isNotEmpty()) {
            for (word in scores.keys.toList()) {
                val used = userModel.frequency(word)
                if (used > 0) scores[word] = scores.getValue(word) + used * 250_000
                val bigram = userModel.bigramFrequency(previousWord, word)
                if (bigram > 0) scores[word] = scores.getValue(word) + bigram * 400_000
            }
        }

        val ordered = scores.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.length })
            .map { it.key }
            .take(limit)

        val converted = if (traditional) ordered.map { converter.convert(it) } else ordered
        val distinct = converted.distinct()
        resultCache[cacheKey] = distinct
        return distinct
    }

    /** Next-word predictions from the user's own bigram statistics. */
    fun predictions(previousWord: String?, userModel: UserModel, limit: Int = 8): List<String> {
        if (previousWord.isNullOrEmpty()) return emptyList()
        return userModel.predictions(previousWord, limit)
    }

    /**
     * Word-level sentence composition, used when no single dictionary entry
     * covers the whole input ("jintiantianqihenhao" -> 今天天气很好).
     *
     * A small beam search over syllable positions prefers real multi-syllable
     * words over character-by-character guesses, which is what makes the
     * result read like Chinese instead of random homophones.
     */
    private fun composeSentences(syllables: List<String>, limit: Int): List<String> {
        val n = syllables.size
        if (n < 2 || n > 24) return emptyList()
        data class Hyp(val text: String, val score: Double)

        val hyps = Array(n + 1) { ArrayList<Hyp>(8) }
        hyps[0].add(Hyp("", 0.0))

        for (i in 0 until n) {
            if (hyps[i].isEmpty()) continue
            hyps[i].sortByDescending { it.score }
            if (hyps[i].size > BEAM) {
                val trimmed = ArrayList<Hyp>(BEAM)
                for (k in 0 until BEAM) trimmed.add(hyps[i][k])
                hyps[i] = trimmed
            }
            val maxLen = minOf(4, n - i)
            for (len in 1..maxLen) {
                val key = syllables.subList(i, i + len).joinToString(" ")
                val entries = dictionary.exact(key, 8).filter { it.word.length == len }
                if (entries.isEmpty()) continue
                for (entry in entries) {
                    // Reward covering more syllables with one real word, which is
                    // what separates 今天天气 from 金天天其.
                    val gain = ln(entry.weight.toDouble() + 1.0) +
                            SEGMENT_BONUS * (len - 1) - SEGMENT_COST
                    for (h in hyps[i]) {
                        hyps[i + len].add(Hyp(h.text + entry.word, h.score + gain))
                    }
                }
            }
        }

        val finalHyps = hyps[n].sortedByDescending { it.score }
        if (finalHyps.isNotEmpty()) {
            return finalHyps.map { it.text }.distinct().take(limit)
        }
        // Fall back to single characters when no word covers the input.
        var beam = listOf(Hyp("", 0.0))
        for (syllable in syllables) {
            val chars = dictionary.exact(syllable, 8).filter { it.word.length == 1 }
            if (chars.isEmpty()) return emptyList()
            val next = ArrayList<Hyp>(beam.size * chars.size)
            for (hyp in beam) {
                for (entry in chars.take(5)) {
                    next.add(Hyp(hyp.text + entry.word, hyp.score + ln(entry.weight.toDouble() + 1.0)))
                }
            }
            beam = next.sortedByDescending { it.score }.take(BEAM)
        }
        return beam.map { it.text }.distinct().take(limit)
    }

    private fun bump(scores: HashMap<String, Int>, word: String, score: Int) {
        if (word.isEmpty()) return
        val current = scores[word]
        if (current == null || score > current) scores[word] = score
    }

    companion object {
        private const val MAX_INPUT = 48
        private const val BEAM = 4
        private const val SEGMENT_BONUS = 12.0
        private const val SEGMENT_COST = 5.0

        @Volatile
        private var instance: PinyinEngine? = null

        @Synchronized
        fun get(context: Context): PinyinEngine {
            instance?.let { return it }
            val dict = AssetDictionary.load(context.applicationContext)
            val conv = TraditionalConverter.load(context.applicationContext)
            val engine = PinyinEngine(dict, conv, PinyinSegmenter(dict.syllables))
            instance = engine
            return engine
        }

        /** Builds an engine from pre-loaded tables (used by the test suite). */
        internal fun create(
            dictionary: AssetDictionary,
            converter: TraditionalConverter
        ): PinyinEngine = PinyinEngine(dictionary, converter, PinyinSegmenter(dictionary.syllables))

        /** Lower-cases, drops separators, keeps letters and apostrophes. */
        fun normalize(input: String): String {
            val sb = StringBuilder(input.length)
            for (ch in input) {
                when {
                    ch in 'a'..'z' -> sb.append(ch)
                    ch in 'A'..'Z' -> sb.append(ch.lowercaseChar())
                    ch == '\'' -> sb.append('\'')
                }
            }
            val cleaned = sb.toString().replace("'", "")
            return if (cleaned.length > MAX_INPUT) cleaned.substring(0, MAX_INPUT) else cleaned
        }
    }
}
