package com.esmer.queenfinder

import com.esmer.queenfinder.detection.Box
import com.esmer.queenfinder.detection.Detection
import com.esmer.queenfinder.detection.TrackFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackFilterTest {
    private fun queen(l: Float, t: Float) =
        Detection(0, "queen", 0.8f, Box(l, t, l + 0.1f, t + 0.1f))

    @Test
    fun singleFrameIsNotStable() {
        val f = TrackFilter(stableFrames = 3)
        assertNull(f.update(listOf(queen(0.2f, 0.2f))))
        assertFalse(f.newlyStable)
    }

    @Test
    fun becomesStableAfterConsecutiveHits() {
        val f = TrackFilter(stableFrames = 3)
        f.update(listOf(queen(0.2f, 0.2f)))
        f.update(listOf(queen(0.21f, 0.2f)))
        val stable = f.update(listOf(queen(0.22f, 0.21f)))
        assertNotNull(stable)
        assertTrue(f.newlyStable)
        // Next frame it is still stable but no new alert.
        f.update(listOf(queen(0.23f, 0.21f)))
        assertFalse(f.newlyStable)
    }

    @Test
    fun jumpingBoxDoesNotCountAsSameTrack() {
        val f = TrackFilter(stableFrames = 2)
        f.update(listOf(queen(0.1f, 0.1f)))
        assertNull(f.update(listOf(queen(0.8f, 0.8f))))
    }

    @Test
    fun trackDiesAfterMisses() {
        val f = TrackFilter(stableFrames = 2, maxMisses = 1)
        f.update(listOf(queen(0.2f, 0.2f)))
        f.update(listOf(queen(0.2f, 0.2f)))
        assertNotNull(f.update(listOf(queen(0.2f, 0.2f))))
        f.update(emptyList())
        f.update(emptyList())
        // Track gone; a new detection starts from scratch.
        assertNull(f.update(listOf(queen(0.2f, 0.2f))))
    }
}
