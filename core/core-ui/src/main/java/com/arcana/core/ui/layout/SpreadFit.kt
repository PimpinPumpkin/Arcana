package com.arcana.core.ui.layout

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Where the cards of a spread go on a board of a given width, and how big they can be.
 *
 * A spread stores each position as a point in a unit square. This keeps the shape the spread was
 * drawn with (rows stay rows, an arch stays an arch) and finds the largest card for which no two
 * cards, and no label and card, touch. It then pulls the layout as tight as it will go, so the
 * board is only as tall as it needs to be.
 *
 * Plain math, no Compose: sizes are in whatever unit the caller uses, as long as it is one unit.
 */
object SpreadFit {

    /** One place on the board: a card, or cards deliberately laid on top of each other. */
    data class Slot(
        val x: Float,
        val y: Float,
        /** One entry per card in the stack, degrees. */
        val rotations: List<Float>,
        /** How wide the label is on a single line, or 0 for no label. */
        val labelWidth: Float = 0f,
    )

    data class Spec(
        val width: Float,
        val maxHeight: Float,
        /** Card width divided by card height. */
        val aspect: Float,
        val minCard: Float,
        val maxCard: Float,
        /** Clear space between neighbors. */
        val gap: Float,
        /** Clear space at the edges of the board. */
        val padding: Float,
        val labelLineHeight: Float = 0f,
        val labelGap: Float = 0f,
        val maxLabelLines: Int = 2,
    )

    data class Placed(
        val centerX: Float,
        val centerY: Float,
        /** Width and height of the box that holds the whole stack, rotations included. */
        val boxWidth: Float,
        val boxHeight: Float,
        val labelLines: Int,
        val labelTop: Float,
    )

    data class Result(
        val cardWidth: Float,
        val cardHeight: Float,
        /** The height the board needs. Never more than the spec allowed. */
        val height: Float,
        val slots: List<Placed>,
        /** False when even the smallest card overlaps: the caller is shown the best that exists. */
        val fits: Boolean,
    )

    /**
     * Positions that share a point are one stack (the crossing card of a Celtic Cross). Returns,
     * for each stack, the indexes into [points] of the cards in it, in their original order.
     */
    fun stacks(points: List<Pair<Float, Float>>, tolerance: Float = 0.02f): List<List<Int>> {
        val groups = ArrayList<MutableList<Int>>()
        for (i in points.indices) {
            val (x, y) = points[i]
            val home = groups.firstOrNull { g ->
                val (gx, gy) = points[g[0]]
                abs(gx - x) <= tolerance && abs(gy - y) <= tolerance
            }
            if (home != null) home += i else groups += mutableListOf(i)
        }
        return groups
    }

    fun fit(slots: List<Slot>, spec: Spec): Result {
        if (slots.isEmpty()) return Result(spec.minCard, spec.minCard / spec.aspect, 0f, emptyList(), true)
        val low = min(spec.minCard, spec.maxCard)
        var best = layout(slots, spec, low)
        if (best == null || !best.clear) {
            // Too many cards for this board even at the smallest size: place them and let them overlap.
            return (best ?: forced(slots, spec, low)).toResult(spec, fits = false)
        }
        var lo = low
        var hi = spec.maxCard
        val top = layout(slots, spec, hi)
        if (top != null && top.clear) {
            best = top
        } else {
            repeat(22) {
                val mid = (lo + hi) / 2f
                val trial = layout(slots, spec, mid)
                if (trial != null && trial.clear) {
                    best = trial
                    lo = mid
                } else {
                    hi = mid
                }
            }
        }
        return tighten(slots, spec, best).toResult(spec, fits = true)
    }

    // ---------------------------------------------------------------------------------------

    /** A candidate: card size, the two scales that spread the unit square out, and the result. */
    private class Layout(
        val card: Float,
        val scaleX: Float,
        val scaleY: Float,
        val boxes: List<Box>,
        val clear: Boolean,
    ) {
        fun toResult(spec: Spec, fits: Boolean): Result {
            val top = boxes.minOf { it.cy - it.up }
            val bottom = boxes.maxOf { it.cy + it.down }
            val left = boxes.minOf { it.cx - it.side }
            val right = boxes.maxOf { it.cx + it.side }
            // Center across the width; sit at the top with only the padding above.
            val dx = (spec.width - (right - left)) / 2f - left
            val dy = spec.padding - top
            val height = min(spec.maxHeight, bottom - top + 2 * spec.padding)
            return Result(
                cardWidth = card,
                cardHeight = card / spec.aspect,
                height = height,
                slots = boxes.map {
                    Placed(it.cx + dx, it.cy + dy, it.side * 2, it.up * 2, it.lines, it.cy + dy + it.up + spec.labelGap)
                },
                fits = fits,
            )
        }
    }

    /** One stack once it has a size: its center, how far it reaches, and its corners for overlap tests. */
    private class Box(
        val cx: Float,
        val cy: Float,
        val side: Float,
        val up: Float,
        val down: Float,
        val lines: Int,
        val rects: List<FloatArray>,
    )

    private class Reach(val side: Float, val up: Float, val down: Float, val lines: Int)

    private fun reach(slot: Slot, spec: Spec, card: Float): Reach {
        val w = card / 2f
        val h = card / spec.aspect / 2f
        var side = 0f
        var up = 0f
        for (deg in slot.rotations.ifEmpty { listOf(0f) }) {
            val r = Math.toRadians(deg.toDouble())
            val c = abs(cos(r)).toFloat()
            val s = abs(sin(r)).toFloat()
            side = max(side, c * w + s * h)
            up = max(up, s * w + c * h)
        }
        val lines = if (slot.labelWidth <= 0f || spec.labelLineHeight <= 0f) 0
        else ceil(slot.labelWidth / max(1f, side * 2)).toInt().coerceIn(1, spec.maxLabelLines)
        val down = up + if (lines > 0) spec.labelGap + lines * spec.labelLineHeight else 0f
        return Reach(side, up, down, lines)
    }

    /**
     * The widest spread of the unit coordinates that still keeps every stack on the board. Returns
     * scale and offset, or null when a stack is wider than the board on its own.
     */
    private fun spread(coords: List<Float>, before: List<Float>, after: List<Float>, room: Float): Pair<Float, Float>? {
        var scale = Float.MAX_VALUE
        for (i in coords.indices) {
            if (before[i] + after[i] > room + 0.01f) return null
            for (j in coords.indices) {
                val d = coords[j] - coords[i]
                if (d > 1e-4f) scale = min(scale, (room - after[j] - before[i]) / d)
            }
        }
        if (scale == Float.MAX_VALUE) scale = 0f
        if (scale < 0f) return null
        return scale to center(coords, before, after, room, scale)
    }

    private fun center(coords: List<Float>, before: List<Float>, after: List<Float>, room: Float, scale: Float): Float {
        var lo = -Float.MAX_VALUE
        var hi = Float.MAX_VALUE
        for (i in coords.indices) {
            lo = max(lo, before[i] - scale * coords[i])
            hi = min(hi, room - after[i] - scale * coords[i])
        }
        return (lo + hi) / 2f
    }

    private fun layout(slots: List<Slot>, spec: Spec, card: Float): Layout? {
        val reaches = slots.map { reach(it, spec, card) }
        val roomX = spec.width - 2 * spec.padding
        val roomY = spec.maxHeight - 2 * spec.padding
        val (sx, _) = spread(slots.map { it.x }, reaches.map { it.side }, reaches.map { it.side }, roomX) ?: return null
        val (sy, _) = spread(slots.map { it.y }, reaches.map { it.up }, reaches.map { it.down }, roomY) ?: return null
        return place(slots, spec, card, reaches, sx, sy)
    }

    /** Used only when nothing fits: spread as far as the board allows and accept the overlap. */
    private fun forced(slots: List<Slot>, spec: Spec, card: Float): Layout {
        val reaches = slots.map { reach(it, spec, card) }
        val roomX = max(spec.width - 2 * spec.padding, reaches.maxOf { it.side * 2 })
        val roomY = max(spec.maxHeight - 2 * spec.padding, reaches.maxOf { it.up + it.down })
        val sx = spread(slots.map { it.x }, reaches.map { it.side }, reaches.map { it.side }, roomX)?.first ?: 0f
        val sy = spread(slots.map { it.y }, reaches.map { it.up }, reaches.map { it.down }, roomY)?.first ?: 0f
        return place(slots, spec, card, reaches, sx, sy)
    }

    /**
     * @param air extra clear space wanted to the sides of each card, on top of the gap. Used to
     *   let a layout breathe when the board is wider than it needs.
     */
    private fun place(slots: List<Slot>, spec: Spec, card: Float, reaches: List<Reach>, sx: Float, sy: Float, air: Float = 0f): Layout {
        val roomX = spec.width - 2 * spec.padding
        val roomY = spec.maxHeight - 2 * spec.padding
        val ox = spec.padding + center(slots.map { it.x }, reaches.map { it.side }, reaches.map { it.side }, roomX, sx)
        val oy = spec.padding + center(slots.map { it.y }, reaches.map { it.up }, reaches.map { it.down }, roomY, sy)
        val w = card / 2f
        val h = card / spec.aspect / 2f
        val grow = spec.gap / 2f
        val boxes = slots.mapIndexed { i, slot ->
            val cx = ox + sx * slot.x
            val cy = oy + sy * slot.y
            val r = reaches[i]
            val rects = ArrayList<FloatArray>()
            for (deg in slot.rotations.ifEmpty { listOf(0f) }) {
                // A turned card has no "sides" to speak of, so it gets the air all round.
                val turned = abs(deg % 180f) > 0.5f
                rects += corners(cx, cy, w + grow + air, h + grow + if (turned) air else 0f, deg)
            }
            if (r.lines > 0) {
                val labelHalf = r.lines * spec.labelLineHeight / 2f
                val labelCy = cy + r.up + spec.labelGap + labelHalf
                rects += corners(cx, labelCy, r.side + grow + air, labelHalf + grow, 0f)
            }
            Box(cx, cy, r.side, r.up, r.down, r.lines, rects)
        }
        var clear = true
        outer@ for (i in boxes.indices) {
            for (j in i + 1 until boxes.size) {
                for (a in boxes[i].rects) for (b in boxes[j].rects) {
                    if (overlaps(a, b)) {
                        clear = false
                        break@outer
                    }
                }
            }
        }
        return Layout(card, sx, sy, boxes, clear)
    }

    /**
     * Pulls the rows together as far as they go without anything touching, then the columns.
     * Columns keep a little more air than the bare gap when the board has width to spare, so a
     * layout that is short of height does not end up as a narrow stack in a wide empty board.
     */
    private fun tighten(slots: List<Slot>, spec: Spec, start: Layout): Layout {
        val reaches = slots.map { reach(it, spec, start.card) }
        fun attempt(sx: Float, sy: Float, air: Float = 0f) = place(slots, spec, start.card, reaches, sx, sy, air)
        val sy = smallest(start.scaleY) { attempt(start.scaleX, it).clear }
        val air = start.card * SIDE_AIR
        val sx = if (attempt(start.scaleX, sy, air).clear) smallest(start.scaleX) { attempt(it, sy, air).clear } else start.scaleX
        val tight = attempt(sx, sy)
        return if (tight.clear) tight else start
    }

    /** Extra space to each side of a card, as a share of its width, when there is room for it. */
    private const val SIDE_AIR = 0.09f

    /** The smallest value in 0..[from] that [ok] accepts, given that [from] is accepted. */
    private fun smallest(from: Float, ok: (Float) -> Boolean): Float {
        if (from <= 0f) return 0f
        if (ok(0f)) return 0f
        var lo = 0f
        var hi = from
        repeat(20) {
            val mid = (lo + hi) / 2f
            if (ok(mid)) hi = mid else lo = mid
        }
        return hi
    }

    /** The four corners of a rectangle turned by [degrees] about its center, as x0,y0,...,x3,y3. */
    private fun corners(cx: Float, cy: Float, halfW: Float, halfH: Float, degrees: Float): FloatArray {
        val r = Math.toRadians(degrees.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        val out = FloatArray(8)
        val xs = floatArrayOf(-halfW, halfW, halfW, -halfW)
        val ys = floatArrayOf(-halfH, -halfH, halfH, halfH)
        for (k in 0 until 4) {
            out[k * 2] = cx + xs[k] * c - ys[k] * s
            out[k * 2 + 1] = cy + xs[k] * s + ys[k] * c
        }
        return out
    }

    /** Separating-axis test for two convex quadrilaterals. Touching edges do not count. */
    private fun overlaps(a: FloatArray, b: FloatArray): Boolean {
        for (quad in arrayOf(a, b)) {
            for (k in 0 until 2) {
                val ex = quad[(k + 1) * 2] - quad[k * 2]
                val ey = quad[(k + 1) * 2 + 1] - quad[k * 2 + 1]
                // The edge's normal is the axis.
                val ax = -ey
                val ay = ex
                var minA = Float.MAX_VALUE
                var maxA = -Float.MAX_VALUE
                var minB = Float.MAX_VALUE
                var maxB = -Float.MAX_VALUE
                for (p in 0 until 4) {
                    val pa = a[p * 2] * ax + a[p * 2 + 1] * ay
                    minA = min(minA, pa)
                    maxA = max(maxA, pa)
                    val pb = b[p * 2] * ax + b[p * 2 + 1] * ay
                    minB = min(minB, pb)
                    maxB = max(maxB, pb)
                }
                val slack = 0.01f * (abs(ax) + abs(ay))
                if (maxA <= minB + slack || maxB <= minA + slack) return false
            }
        }
        return true
    }
}
