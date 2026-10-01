package com.arcana.core.ui.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class SpreadFitTest {
    private val aspect = 0.62f

    private fun spec(width: Float = 395f, maxHeight: Float = 700f, maxCard: Float = 240f, labels: Boolean = false) = SpreadFit.Spec(
        width = width,
        maxHeight = maxHeight,
        aspect = aspect,
        minCard = 40f,
        maxCard = maxCard,
        gap = 8f,
        padding = 8f,
        labelLineHeight = if (labels) 16f else 0f,
        labelGap = 4f,
    )

    private fun slot(x: Float, y: Float, vararg rotations: Float, label: Float = 0f) =
        SpreadFit.Slot(x, y, if (rotations.isEmpty()) listOf(0f) else rotations.toList(), label)

    /** Upright stacks only: plain rectangles, checked without the code under test. */
    private fun assertApart(result: SpreadFit.Result, spec: SpreadFit.Spec) {
        val boxes = result.slots.map {
            val bottom = if (it.labelLines > 0) it.labelTop + it.labelLines * spec.labelLineHeight else it.centerY + it.boxHeight / 2
            floatArrayOf(it.centerX - it.boxWidth / 2, it.centerY - it.boxHeight / 2, it.centerX + it.boxWidth / 2, bottom)
        }
        for (b in boxes) {
            assertTrue("left edge ${b[0]}", b[0] >= spec.padding - 0.5f)
            assertTrue("right edge ${b[2]}", b[2] <= spec.width - spec.padding + 0.5f)
            assertTrue("top edge ${b[1]}", b[1] >= spec.padding - 0.5f)
            assertTrue("bottom edge ${b[3]} of ${result.height}", b[3] <= result.height - spec.padding + 0.5f)
        }
        for (i in boxes.indices) for (j in i + 1 until boxes.size) {
            val a = boxes[i]
            val b = boxes[j]
            val apartX = a[2] + spec.gap <= b[0] + 0.5f || b[2] + spec.gap <= a[0] + 0.5f
            val apartY = a[3] + spec.gap <= b[1] + 0.5f || b[3] + spec.gap <= a[1] + 0.5f
            assertTrue("slots $i and $j touch", apartX || apartY)
        }
    }

    @Test
    fun `a single card is as large as allowed and centered`() {
        val s = spec()
        val r = SpreadFit.fit(listOf(slot(0.5f, 0.5f)), s)
        assertTrue(r.fits)
        assertEquals(240f, r.cardWidth, 0.5f)
        assertEquals(395f / 2, r.slots[0].centerX, 0.5f)
        assertEquals(240f / aspect + 16f, r.height, 1f)
    }

    @Test
    fun `a single card shrinks to the height it is given`() {
        val r = SpreadFit.fit(listOf(slot(0.5f, 0.5f)), spec(maxHeight = 216f))
        assertEquals(200f * aspect, r.cardWidth, 1f)
    }

    @Test
    fun `three in a row fill the width`() {
        val s = spec()
        val r = SpreadFit.fit(listOf(slot(0.2f, 0.5f), slot(0.5f, 0.5f), slot(0.8f, 0.5f)), s)
        assertTrue(r.fits)
        // 395 wide, 8 at each edge, 8 between cards: (395 - 16 - 16) / 3.
        assertEquals(121f, r.cardWidth, 1f)
        assertApart(r, s)
        // The board is one row tall, not the whole height it was offered.
        assertEquals(r.cardHeight + 16f, r.height, 1f)
    }

    @Test
    fun `labels sit under their cards and push the rows apart`() {
        val s = spec(labels = true)
        val slots = listOf(
            slot(0.2f, 0.65f, label = 60f), slot(0.5f, 0.65f, label = 60f), slot(0.8f, 0.65f, label = 300f),
            slot(0.35f, 0.3f, label = 60f), slot(0.65f, 0.3f, label = 60f),
        )
        val r = SpreadFit.fit(slots, s)
        assertTrue(r.fits)
        assertApart(r, s)
        assertEquals(1, r.slots[0].labelLines)
        // A label too long for two lines is still held to two.
        assertEquals(2, r.slots[2].labelLines)
        assertTrue(r.slots[3].centerY < r.slots[0].centerY)
    }

    @Test
    fun `stacked cards share a center and are one slot`() {
        val points = listOf(0.35f to 0.5f, 0.35f to 0.5f, 0.35f to 0.78f, 0.15f to 0.5f)
        val stacks = SpreadFit.stacks(points)
        assertEquals(listOf(listOf(0, 1), listOf(2), listOf(3)), stacks)
    }

    @Test
    fun `a celtic cross fits a phone without overlap`() {
        val s = spec(labels = true)
        val slots = listOf(
            slot(0.384f, 0.50f, 0f, 90f, label = 140f), // the heart and the card across it
            slot(0.384f, 0.74f, label = 70f),
            slot(0.12f, 0.50f, label = 60f),
            slot(0.384f, 0.26f, label = 45f),
            slot(0.648f, 0.50f, label = 100f),
            slot(0.88f, 0.86f, label = 30f),
            slot(0.88f, 0.62f, label = 80f),
            slot(0.88f, 0.38f, label = 110f),
            slot(0.88f, 0.14f, label = 60f),
        )
        val r = SpreadFit.fit(slots, s)
        assertTrue(r.fits)
        assertApart(r, s)
        // The crossing card makes the middle slot as wide as a card is tall.
        assertEquals(r.cardHeight, r.slots[0].boxWidth, 0.5f)
        assertTrue("cards came out ${r.cardWidth} wide", r.cardWidth > 70f)
    }

    @Test
    fun `turned cards do not touch`() {
        // Two cards side by side, one turned a quarter: the turned one needs its height in width.
        val s = spec(maxCard = 500f)
        val r = SpreadFit.fit(listOf(slot(0.25f, 0.5f), slot(0.75f, 0.5f, 90f)), s)
        assertTrue(r.fits)
        val left = r.slots[0]
        val right = r.slots[1]
        assertTrue(left.centerX + left.boxWidth / 2 + s.gap <= right.centerX - right.boxWidth / 2 + 0.5f)
        assertEquals(r.cardHeight, right.boxWidth, 0.5f)
    }

    @Test
    fun `a full six by four grid still fits`() {
        val s = spec(maxHeight = 1000f)
        val slots = (0 until 6).flatMap { row -> (0 until 4).map { col -> slot((col + 0.5f) / 4, (row + 0.5f) / 6) } }
        val r = SpreadFit.fit(slots, s)
        assertTrue(r.fits)
        assertApart(r, s)
    }

    @Test
    fun `too many cards for the board is reported, not hidden`() {
        val s = spec(width = 120f, maxHeight = 100f)
        val slots = (0 until 6).map { slot(it / 5f, 0.5f) }
        assertFalse(SpreadFit.fit(slots, s).fits)
    }

    @Test
    fun `random upright layouts never overlap when they are said to fit`() {
        val random = Random(7)
        repeat(300) {
            val n = random.nextInt(1, 13)
            val slots = List(n) { slot(random.nextInt(0, 5) / 4f, random.nextInt(0, 7) / 6f) }.distinctBy { it.x to it.y }
            val s = spec(width = random.nextInt(300, 800).toFloat(), maxHeight = random.nextInt(250, 1200).toFloat(), labels = random.nextBoolean())
            val r = SpreadFit.fit(slots.map { it.copy(labelWidth = random.nextInt(0, 200).toFloat()) }, s)
            if (r.fits) assertApart(r, s)
            assertTrue(r.height <= s.maxHeight + 0.5f)
            assertTrue(abs(r.cardHeight * aspect - r.cardWidth) < 0.01f)
        }
    }
}
