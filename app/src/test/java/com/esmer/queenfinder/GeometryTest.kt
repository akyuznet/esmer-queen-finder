package com.esmer.queenfinder

import com.esmer.queenfinder.detection.Box
import com.esmer.queenfinder.detection.rotateBox
import org.junit.Assert.assertEquals
import org.junit.Test

class GeometryTest {
    private val eps = 1e-6f

    private fun assertBox(expected: Box, actual: Box) {
        assertEquals(expected.left, actual.left, eps)
        assertEquals(expected.top, actual.top, eps)
        assertEquals(expected.right, actual.right, eps)
        assertEquals(expected.bottom, actual.bottom, eps)
    }

    @Test
    fun zeroDegreesIsIdentity() {
        val b = Box(0.1f, 0.2f, 0.3f, 0.5f)
        assertBox(b, rotateBox(b, 0))
    }

    @Test
    fun ninetyDegreesMovesTopLeftToTopRight() {
        // A box in the top-left of a landscape sensor frame ends up top-right when
        // the frame is rotated 90 degrees clockwise to portrait.
        val b = Box(0.0f, 0.0f, 0.2f, 0.1f)
        assertBox(Box(0.9f, 0.0f, 1.0f, 0.2f), rotateBox(b, 90))
    }

    @Test
    fun twoSeventyDegreesMovesTopLeftToBottomLeft() {
        val b = Box(0.0f, 0.0f, 0.2f, 0.1f)
        assertBox(Box(0.0f, 0.8f, 0.1f, 1.0f), rotateBox(b, 270))
    }

    @Test
    fun oneEightyFlipsBoth() {
        val b = Box(0.0f, 0.0f, 0.2f, 0.1f)
        assertBox(Box(0.8f, 0.9f, 1.0f, 1.0f), rotateBox(b, 180))
    }

    @Test
    fun fourQuarterTurnsReturnToStart() {
        val b = Box(0.1f, 0.2f, 0.3f, 0.5f)
        var r = b
        repeat(4) { r = rotateBox(r, 90) }
        assertBox(b, r)
    }

    @Test
    fun rotatedBoxKeepsPositiveSize() {
        val b = Box(0.1f, 0.2f, 0.3f, 0.5f)
        for (d in listOf(90, 180, 270)) {
            val r = rotateBox(b, d)
            assert(r.width > 0f && r.height > 0f)
        }
    }
}
