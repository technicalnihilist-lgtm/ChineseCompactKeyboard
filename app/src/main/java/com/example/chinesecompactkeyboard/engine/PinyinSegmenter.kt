package com.example.chinesecompactkeyboard.engine

/**
 * Splits a toneless pinyin string into syllables.
 *
 * Input "nihao" yields [[ni, hao]]; "nih" yields [[ni] + partial "h"], which is
 * what makes prefix typing (showing 你好 before the last letter is complete)
 * work. Alternative segmentations are returned too, so "xian" offers both
 * 先 (xian) and 西安 (xi an).
 */
class PinyinSegmenter(private val syllables: Set<String>) {

    data class Segmentation(val syllables: List<String>, val partial: String?) {
        /** The prefix used to search the dictionary, e.g. "ni hao" or "ni h". */
        val query: String
            get() = if (partial.isNullOrEmpty()) syllables.joinToString(" ")
            else (syllables + partial).joinToString(" ")

        val isPartial: Boolean get() = !partial.isNullOrEmpty()
        val syllableCount: Int get() = syllables.size + if (isPartial) 1 else 0
    }

    private val prefixes: MutableSet<String> = HashSet<String>(4096)

    init {
        for (syllable in syllables) {
            for (n in 1..syllable.length) prefixes.add(syllable.substring(0, n))
        }
    }

    private val maxSyllableLength: Int = syllables.maxOfOrNull { it.length } ?: 6

    fun isPrefixOfSyllable(text: String): Boolean = prefixes.contains(text)

    fun segment(input: String, maxResults: Int = 5): List<Segmentation> {
        val results = ArrayList<Segmentation>(maxResults)
        val n = input.length
        if (n == 0) return results
        val acc = ArrayList<String>(8)

        fun dfs(pos: Int) {
            if (results.size >= maxResults) return
            if (pos == n) {
                if (acc.isNotEmpty()) results.add(Segmentation(ArrayList(acc), null))
                return
            }
            val longest = minOf(maxSyllableLength, n - pos)
            var len = longest
            while (len >= 1) {
                val seg = input.substring(pos, pos + len)
                if (syllables.contains(seg)) {
                    acc.add(seg)
                    dfs(pos + len)
                    acc.removeAt(acc.size - 1)
                    if (results.size >= maxResults) return
                }
                len--
            }
            // Unfinished last syllable: "ni h" -> 你 好 ...
            // A complete syllable is never treated as a partial: the loop above
            // already explored it, and adding it again would waste a slot.
            val rest = input.substring(pos)
            if (rest.length <= maxSyllableLength && prefixes.contains(rest) && !syllables.contains(rest)) {
                results.add(Segmentation(ArrayList(acc), rest))
            }
        }

        dfs(0)
        if (results.isEmpty()) {
            // Nothing parsed at all: treat the whole thing as a partial syllable
            // so the user still sees something useful.
            results.add(Segmentation(emptyList(), input.takeLast(maxSyllableLength)))
        }
        return results
    }
}
