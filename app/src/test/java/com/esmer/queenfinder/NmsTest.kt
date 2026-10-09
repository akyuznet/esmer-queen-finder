package com.esmer.queenfinder

import com.esmer.queenfinder.detection.Box
import com.esmer.queenfinder.detection.Detection
import com.esmer.queenfinder.detection.iou
import com.esmer.queenfinder.detection.nms
import org.junit.Assert.assertEquals
import org.junit.Test

class NmsTest {
    private fun det(l: Float, t: Float, r: Float, b: Float, score: Float, cls: Int = 0) =
        Detection(cls, if (cls == 0) "queen" else "drone", score, Box(l, t, r, b))

    @Test
    fun iouOfIdenticalBoxesIsOne() {
        val a = Box(0f, 0f, 1f, 1f)
        assertEquals(1f, iou(a, a.copy()), 1e-6f)
    }

    @Test
    fun iouOfDisjointBoxesIsZero() {
        assertEquals(0f, iou(Box(0f, 0f, 1f, 1f), Box(2f, 2f, 3f, 3f)), 1e-6f)
    }

    @Test
    fun iouOfHalfOverlapIsOneThird() {
        assertEquals(1f / 3f, iou(Box(0f, 0f, 1f, 1f), Box(0.5f, 0f, 1.5f, 1f)), 1e-5f)
    }

    @Test
    fun nmsKeepsHighestAndDropsOverlapOfSameClass() {
        val kept = nms(listOf(det(0f, 0f, 1f, 1f, 0.6f), det(0.05f, 0.05f, 1f, 1f, 0.9f)))
        assertEquals(1, kept.size)
        assertEquals(0.9f, kept[0].score, 1e-6f)
    }

    @Test
    fun nmsKeepsOverlapOfDifferentClass() {
        val kept = nms(listOf(det(0f, 0f, 1f, 1f, 0.6f, cls = 0), det(0f, 0f, 1f, 1f, 0.9f, cls = 1)))
        assertEquals(2, kept.size)
    }
}
