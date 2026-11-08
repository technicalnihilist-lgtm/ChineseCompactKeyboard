package com.example.chinesecompactkeyboard.input

/** What a key does. */
enum class KeyKind { CHAR, FUNCTION, SPACE, ENTER, BACKSPACE, SHIFT, MODE, LAYOUT, MENU }

data class KeySpec(
    val code: String,
    val label: String,
    val kind: KeyKind,
    val weight: Float = 1f
)

data class KeyRow(val keys: List<KeySpec>, val sidePadUnits: Float)

/**
 * Static key layouts. Widths are expressed in "units" where a letters row is
 * 10 units wide, which keeps every row aligned to the same grid on any screen.
 */
object KeyLayouts {

    private fun ch(code: String, weight: Float = 1f) = KeySpec(code, code, KeyKind.CHAR, weight)

    fun letters(chinese: Boolean, shift: Boolean): List<KeyRow> {
        fun letter(c: Char): String = if (shift) c.uppercaseChar().toString() else c.toString()
        val row1 = "qwertyuiop".map { ch(letter(it)) }
        val row2 = "asdfghjkl".map { ch(letter(it)) }
        val row3 = listOf(
            KeySpec("SHIFT", if (shift) "⇧" else "⇧", KeyKind.SHIFT, 1.35f)
        ) + "zxcvbnm".map { ch(letter(it)) } + listOf(
            KeySpec("DEL", "⌫", KeyKind.BACKSPACE, 1.35f)
        )
        val row4 = listOf(
            KeySpec("SYM", "123", KeyKind.LAYOUT, 1.15f),
            KeySpec("MODE", if (chinese) "中" else "EN", KeyKind.MODE, 1.15f),
            KeySpec("MENU", "☰", KeyKind.MENU, 0.95f),
            KeySpec("SPACE", if (chinese) "空格" else "space", KeyKind.SPACE, 2.9f),
            KeySpec(",", if (chinese) "，" else ",", KeyKind.CHAR, 0.9f),
            KeySpec(".", if (chinese) "。" else ".", KeyKind.CHAR, 0.9f),
            KeySpec("ENTER", "↵", KeyKind.ENTER, 1.45f)
        )
        return listOf(
            KeyRow(row1, 0f),
            KeyRow(row2, 0.5f),
            KeyRow(row3, 0.2f),
            KeyRow(row4, 0.2f)
        )
    }

    fun symbols(shift: Boolean): List<KeyRow> {
        val row1 = "1234567890".map { ch(it.toString()) }
        val row2 = listOf("@", "#", "$", "%", "&", "*", "-", "+", "(", ")").map { ch(it) }
        val row3 = listOf("/", "\"", "'", ":", ";", "!", "?").map { ch(it) } + listOf(
            KeySpec("DEL", "⌫", KeyKind.BACKSPACE, 1.3f)
        )
        val row4 = listOf(
            KeySpec("ABC", "ABC", KeyKind.LAYOUT, 1.3f),
            KeySpec("MODE", if (shift) "中" else "EN", KeyKind.MODE, 1.1f),
            KeySpec("MENU", "☰", KeyKind.MENU, 0.95f),
            KeySpec("SPACE", "空格", KeyKind.SPACE, 2.6f),
            KeySpec("_", "_", KeyKind.CHAR, 0.9f),
            KeySpec("ENTER", "↵", KeyKind.ENTER, 1.45f)
        )
        return listOf(
            KeyRow(row1, 0f),
            KeyRow(row2, 0f),
            KeyRow(row3, 0.15f),
            KeyRow(row4, 0.1f)
        )
    }
}
