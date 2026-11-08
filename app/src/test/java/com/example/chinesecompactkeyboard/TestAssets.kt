package com.example.chinesecompactkeyboard

import java.io.File

/** Reads the shipped dictionary parts exactly the way the app does. */
object TestAssets {

    private val assetsDir = File("src/main/assets")

    fun dictionaryBytes(): ByteArray {
        val parts = assetsDir.listFiles { file ->
            file.name.startsWith("pinyin_dict.") && !file.name.endsWith(".bin")
        }?.sortedBy { it.name }.orEmpty()
        if (parts.isEmpty()) return File(assetsDir, "pinyin_dict.bin").readBytes()
        var total = 0
        val chunks = parts.map { it.readBytes() }
        for (chunk in chunks) total += chunk.size
        val joined = ByteArray(total)
        var offset = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, joined, offset, chunk.size)
            offset += chunk.size
        }
        return joined
    }

    fun tradBytes(): ByteArray = File(assetsDir, "trad_map.bin").readBytes()

    fun dictionarySize(): Long {
        val parts = assetsDir.listFiles { file ->
            file.name.startsWith("pinyin_dict.") && !file.name.endsWith(".bin")
        }?.sortedBy { it.name }.orEmpty()
        return if (parts.isEmpty()) File(assetsDir, "pinyin_dict.bin").length()
        else parts.sumOf { it.length() }
    }
}
