package com.arcana.service.ai.local

import java.io.ByteArrayOutputStream

/**
 * Turns the model's output, which arrives as bytes a token at a time, into text. A character can
 * be split across two tokens, so bytes are held back until the character they belong to is whole.
 */
internal class Utf8Stream {
    private val bytes = ByteArrayOutputStream()
    private var shown = 0

    /** Everything decoded so far. */
    val text: String get() = bytes.toByteArray().decodeToString(0, complete(bytes.toByteArray()))

    /** Adds a token's bytes and returns the text that became complete with them. */
    fun push(piece: ByteArray): String {
        bytes.write(piece)
        val all = bytes.toByteArray()
        val end = complete(all)
        if (end <= shown) return ""
        val delta = all.decodeToString(shown, end)
        shown = end
        return delta
    }

    /** The length of the longest prefix of [data] that ends on a character boundary. */
    private fun complete(data: ByteArray): Int {
        var i = data.size - 1
        var back = 0
        // Step back over continuation bytes (10xxxxxx) to the byte that starts the last character.
        while (i >= 0 && back < 3 && (data[i].toInt() and 0xC0) == 0x80) {
            i--
            back++
        }
        if (i < 0) return data.size
        val lead = data[i].toInt() and 0xFF
        val need = when {
            lead < 0x80 -> 1
            lead >= 0xF0 -> 4
            lead >= 0xE0 -> 3
            lead >= 0xC0 -> 2
            else -> 1
        }
        return if (data.size - i >= need) data.size else i
    }
}
