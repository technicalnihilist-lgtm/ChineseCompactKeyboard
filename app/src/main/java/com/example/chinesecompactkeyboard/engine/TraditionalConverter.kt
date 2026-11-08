package com.example.chinesecompactkeyboard.engine

import android.content.Context
import java.io.DataInputStream
import java.io.IOException

/**
 * Simplified -> Traditional conversion using the OpenCC phrase/character
 * tables, compiled into a sorted byte table and applied with longest-match
 * replacement (the same algorithm OpenCC itself uses).
 */
class TraditionalConverter private constructor(
    private val data: ByteArray,
    private val count: Int,
    private val maxKeyLength: Int,
    private val keyOffsets: IntArray,
    private val valueOffsets: IntArray,
    private val keyBase: Int,
    private val valueBase: Int
) {

    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 512
    }

    fun convert(text: String): String {
        if (text.isEmpty()) return text
        cache[text]?.let { return it }
        val out = StringBuilder(text.length + 2)
        var i = 0
        while (i < text.length) {
            var len = minOf(maxKeyLength, text.length - i)
            var matched = false
            while (len >= 1) {
                val key = text.substring(i, i + len)
                val idx = find(key)
                if (idx >= 0) {
                    out.append(valueAt(idx))
                    i += len
                    matched = true
                    break
                }
                len--
            }
            if (!matched) {
                out.append(text[i])
                i++
            }
        }
        val result = out.toString()
        cache[text] = result
        return result
    }

    private fun u8(index: Int): Int = data[index].toInt() and 0xFF

    private fun keyLength(i: Int): Int = keyOffsets[i + 1] - keyOffsets[i]

    private fun valueAt(i: Int): String =
        String(data, valueBase + valueOffsets[i], valueOffsets[i + 1] - valueOffsets[i], Charsets.UTF_8)

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

    private fun find(key: String): Int {
        val q = key.toByteArray(Charsets.UTF_8)
        var lo = 0
        var hi = count
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            val cmp = compareKey(mid, q)
            if (cmp < 0) lo = mid + 1
            else if (cmp > 0) hi = mid
            else return mid
        }
        return -1
    }

    companion object {
        private const val ASSET = "trad_map.bin"
        private const val MAGIC = 0x54534B43 // "CKST"

        fun load(context: Context): TraditionalConverter =
            loadFromBytes(context.assets.open(ASSET).use { DataInputStream(it).readBytes() })

        fun loadFromBytes(bytes: ByteArray): TraditionalConverter {
            fun i32(off: Int): Int =
                (bytes[off].toInt() and 0xFF) or ((bytes[off + 1].toInt() and 0xFF) shl 8) or
                        ((bytes[off + 2].toInt() and 0xFF) shl 16) or ((bytes[off + 3].toInt() and 0xFF) shl 24)

            if (i32(0) != MAGIC) throw IOException("bad conversion table magic")
            val count = i32(8)
            val maxKeyLength = i32(12)
            var pos = 16
            val keyOffsets = IntArray(count + 1) { i32(pos + it * 4) }
            pos += (count + 1) * 4
            val valueOffsets = IntArray(count + 1) { i32(pos + it * 4) }
            pos += (count + 1) * 4
            val keyBase = pos
            val valueBase = keyBase + keyOffsets[count]
            return TraditionalConverter(
                bytes, count, maxKeyLength, keyOffsets, valueOffsets, keyBase, valueBase
            )
        }
    }
}
