package com.arcana.service.ai.local

import org.junit.Assert.assertEquals
import org.junit.Test

class Utf8StreamTest {
    @Test
    fun `plain text passes straight through`() {
        val stream = Utf8Stream()
        assertEquals("You ", stream.push("You ".toByteArray()))
        assertEquals("are", stream.push("are".toByteArray()))
        assertEquals("You are", stream.text)
    }

    @Test
    fun `a character split across two tokens is held until it is whole`() {
        val stream = Utf8Stream()
        val apostrophe = "’".toByteArray() // three bytes
        assertEquals("You", stream.push("You".toByteArray() + apostrophe.copyOfRange(0, 1)))
        assertEquals("", stream.push(apostrophe.copyOfRange(1, 2)))
        assertEquals("’re", stream.push(apostrophe.copyOfRange(2, 3) + "re".toByteArray()))
        assertEquals("You’re", stream.text)
    }

    @Test
    fun `a four byte character arriving a byte at a time comes out once`() {
        val stream = Utf8Stream()
        val moon = "🌙".toByteArray()
        assertEquals(4, moon.size)
        val out = moon.map { stream.push(byteArrayOf(it)) }
        assertEquals(listOf("", "", "", "🌙"), out)
    }

    @Test
    fun `text leaves out a character that never finished`() {
        val stream = Utf8Stream()
        stream.push("ok".toByteArray() + "é".toByteArray().copyOfRange(0, 1))
        assertEquals("ok", stream.text)
    }
}
