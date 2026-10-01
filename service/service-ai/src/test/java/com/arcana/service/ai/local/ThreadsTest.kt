package com.arcana.service.ai.local

import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadsTest {
    private val slow = 1_804_800L
    private val mid = 2_208_000L
    private val fast = 2_400_000L

    @Test
    fun `two fast cores and six slow write on two and read on all eight`() {
        assertEquals(2 to 8, Threads.pick(8, List(6) { slow } + List(2) { fast }))
    }

    @Test
    fun `three kinds of core count everything above the slowest as fast`() {
        assertEquals(4 to 8, Threads.pick(8, List(4) { slow } + List(3) { mid } + fast))
    }

    @Test
    fun `cores that are all alike use half for writing`() {
        assertEquals(4 to 8, Threads.pick(8, List(8) { fast }))
        assertEquals(2 to 4, Threads.pick(4, List(4) { slow }))
    }

    @Test
    fun `unknown speeds fall back to half the cores`() {
        assertEquals(4 to 8, Threads.pick(8, emptyList()))
        assertEquals(3 to 6, Threads.pick(6, listOf(fast, fast)))
    }

    @Test
    fun `never more threads than cores`() {
        assertEquals(1 to 1, Threads.pick(1, listOf(slow)))
        assertEquals(2 to 2, Threads.pick(2, listOf(slow, fast)))
        assertEquals(4 to 8, Threads.pick(12, List(12) { fast }))
    }
}
