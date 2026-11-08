package com.example.chinesecompactkeyboard.engine

import android.content.Context
import java.io.DataInputStream
import java.io.IOException

/**
 * Memory-mapped style reader for the offline pinyin dictionary shipped in assets.
 *
 * Layout (little endian):
 *   "CKPY" | version | count | maxWordLen | keyBlobLen | wordBlobLen | sylBlobLen
 *   keyOffsets[count + 1] | wordOffsets[count + 1] | weights[count]
 *   keyBlob (ASCII, sorted) | wordBlob (UTF-8) | syllableBlob (ASCII, '\n' separated)
 *
 * Keys are ASCII, so the file order equals String order and a plain binary
 * search over the raw bytes works. Only the handful of words that end up in a
 * candidate list are ever decoded into String objects, which keeps the heap
 * footprint at roughly the size of the file itself.
 */
class AssetDictionary private constructor(
    private val data: ByteArray,
    val count: Int,
    val maxWordLen: Int,
    private val keyOffsets: IntArray,
    private val wordOffsets: IntArray,
    private val weights: IntArray,
    private val keyBase: Int,
    private val wordBase: Int,
    val syllables: Set<String>
) {

    private fun u8(index: Int): Int = data[index].toInt() and 0xFF

    private fun readInt(offset: Int): Int =
        u8(offset) or (u8(offset + 1) shl 8) or (u8(offset + 2) shl 16) or (u8(offset + 3) shl 24)

    private fun keyLength(i: Int): Int = keyOffsets[i + 1] - keyOffsets[i]

    /** Compares entry [i]'s key with the ASCII query [q]; String-like ordering. */
    private fun compareKey(i: Int, q: ByteArray): Int {
        val start = keyBase + keyOffsets[i]
        val len = keyLength(i)
        val n = if (len < q.size) len else q.size
        var j = 0
        while (j < n) {
            val a = u8(start + j)
            val b = q[j].toInt() and 0xFF
            if (a != b) return a - b
            j++
        }
        return len - q.size
    }

    private fun startsWith(i: Int, q: ByteArray): Boolean {
        val len = keyLength(i)
        if (len < q.size) return false
        val start = keyBase + keyOffsets[i]
        var j = 0
        while (j < q.size) {
            if (u8(start + j) != (q[j].toInt() and 0xFF)) return false
            j++
        }
        return true
    }

    private fun lowerBound(q: ByteArray): Int {
        var lo = 0
        var hi = count
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (compareKey(mid, q) < 0) lo = mid + 1 else hi = mid
        }
        return lo
    }

    fun keyAt(i: Int): String {
        val start = keyBase + keyOffsets[i]
        return String(data, start, keyLength(i), Charsets.US_ASCII)
    }

    fun wordAt(i: Int): String {
        val start = wordBase + wordOffsets[i]
        return String(data, start, wordOffsets[i + 1] - wordOffsets[i], Charsets.UTF_8)
    }

    fun weightAt(i: Int): Int = weights[i]

    /** Number of UTF-16 chars in entry [i]'s word, without decoding it. */
    fun wordLengthAt(i: Int): Int {
        val start = wordBase + wordOffsets[i]
        val end = wordBase + wordOffsets[i + 1]
        var n = 0
        var p = start
        while (p < end) {
            val b = u8(p)
            if (b and 0xC0 != 0x80) n++
            p++
        }
        return n
    }

    /** All entries whose key is exactly [key]. */
    fun exact(key: String, limit: Int): List<Entry> {
        val q = key.toByteArray(Charsets.US_ASCII)
        var i = lowerBound(q)
        val out = ArrayList<Entry>(minOf(limit, 16))
        while (i < count && compareKey(i, q) == 0) {
            out.add(Entry(wordAt(i), weights[i], i))
            if (out.size >= limit) break
            i++
        }
        out.sortByDescending { it.weight }
        return out
    }

    /**
     * Entries whose key starts with [prefix], best [limit] by weight.
     * [maxScan] bounds the work for very broad prefixes such as "a ".
     */
    fun prefix(prefix: String, limit: Int, maxScan: Int = 4000): List<Entry> {
        val q = prefix.toByteArray(Charsets.US_ASCII)
        var i = lowerBound(q)
        val bestIdx = IntArray(limit)
        val bestW = IntArray(limit)
        java.util.Arrays.fill(bestW, Int.MIN_VALUE)
        var filled = 0
        var minPos = 0
        var scanned = 0
        while (i < count && scanned < maxScan && startsWith(i, q)) {
            val w = weights[i]
            if (filled < limit) {
                bestIdx[filled] = i
                bestW[filled] = w
                filled++
                if (filled == limit) minPos = indexOfMin(bestW, filled)
            } else if (w > bestW[minPos]) {
                bestIdx[minPos] = i
                bestW[minPos] = w
                minPos = indexOfMin(bestW, filled)
            }
            i++
            scanned++
        }
        if (filled == 0) return emptyList()
        val order = (0 until filled).sortedByDescending { bestW[it] }
        val out = ArrayList<Entry>(filled)
        for (k in order) {
            val idx = bestIdx[k]
            out.add(Entry(wordAt(idx), bestW[k], idx))
        }
        return out
    }

    private fun indexOfMin(values: IntArray, size: Int): Int {
        var minPos = 0
        var min = values[0]
        for (k in 1 until size) {
            if (values[k] < min) {
                min = values[k]
                minPos = k
            }
        }
        return minPos
    }

    data class Entry(val word: String, val weight: Int, val index: Int)

    companion object {
        const val ASSET = "pinyin_dict.bin"
        private const val MAGIC = 0x59504B43 // "CKPY" little endian

        /**
         * Loads the dictionary from assets. The data ships as `pinyin_dict.0`,
         * `pinyin_dict.1`, ... because very large single assets are awkward for
         * repository tooling; they are concatenated here and parsed once.
         */
        fun load(context: Context): AssetDictionary {
            val assets = context.assets
            val parts = (assets.list("") ?: emptyArray())
                .filter { it.startsWith("pinyin_dict.") && !it.endsWith(".bin") }
                .sorted()
            val bytes = if (parts.isEmpty()) {
                assets.open(ASSET).use { DataInputStream(it).readBytes() }
            } else {
                var total = 0
                val chunks = ArrayList<ByteArray>(parts.size)
                for (name in parts) {
                    val chunk = assets.open(name).use { DataInputStream(it).readBytes() }
                    chunks.add(chunk)
                    total += chunk.size
                }
                val joined = ByteArray(total)
                var offset = 0
                for (chunk in chunks) {
                    System.arraycopy(chunk, 0, joined, offset, chunk.size)
                    offset += chunk.size
                }
                joined
            }
            return loadFromBytes(bytes)
        }

        /** Parses a dictionary image; used by [load] and by the JVM test suite. */
        fun loadFromBytes(bytes: ByteArray): AssetDictionary {
            if (bytes.size < 28) throw IOException("dictionary asset is truncated")
            fun i32(off: Int): Int =
                (bytes[off].toInt() and 0xFF) or ((bytes[off + 1].toInt() and 0xFF) shl 8) or
                        ((bytes[off + 2].toInt() and 0xFF) shl 16) or ((bytes[off + 3].toInt() and 0xFF) shl 24)

            if (i32(0) != MAGIC) throw IOException("bad dictionary magic")
            val version = i32(4)
            if (version < 3) throw IOException("unsupported dictionary version $version")
            val count = i32(8)
            val maxWordLen = i32(12)
            val keyBlobLen = i32(16)
            val wordBlobLen = i32(20)
            val sylBlobLen = i32(24)

            var pos = 28
            val keyOffsets = IntArray(count + 1) { i32(pos + it * 4) }
            pos += (count + 1) * 4
            val wordOffsets = IntArray(count + 1) { i32(pos + it * 4) }
            pos += (count + 1) * 4
            val weights = IntArray(count) { i32(pos + it * 4) }
            pos += count * 4
            val keyBase = pos
            val wordBase = keyBase + keyBlobLen
            val sylBase = wordBase + wordBlobLen

            val syllables = HashSet<String>(1024)
            if (sylBlobLen > 0) {
                var start = sylBase
                var p = sylBase
                val end = sylBase + sylBlobLen
                while (p <= end) {
                    if (p == end || bytes[p] == '\n'.code.toByte()) {
                        if (p > start) syllables.add(String(bytes, start, p - start, Charsets.US_ASCII))
                        start = p + 1
                    }
                    p++
                }
            }
            return AssetDictionary(
                bytes, count, maxWordLen, keyOffsets, wordOffsets, weights,
                keyBase, wordBase, syllables
            )
        }
    }
}
