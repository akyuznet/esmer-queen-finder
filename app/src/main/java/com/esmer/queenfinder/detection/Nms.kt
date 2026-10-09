package com.esmer.queenfinder.detection

import kotlin.math.max
import kotlin.math.min

/** Intersection over union of two boxes. */
fun iou(a: Box, b: Box): Float {
    val left = max(a.left, b.left)
    val top = max(a.top, b.top)
    val right = min(a.right, b.right)
    val bottom = min(a.bottom, b.bottom)
    val w = right - left
    val h = bottom - top
    if (w <= 0f || h <= 0f) return 0f
    val inter = w * h
    val union = a.area + b.area - inter
    return if (union <= 0f) 0f else inter / union
}

/**
 * Class-aware non-maximum suppression. Keeps the highest scoring box and drops
 * any other box of the same class that overlaps it by more than [iouThreshold].
 */
fun nms(candidates: List<Detection>, iouThreshold: Float = 0.45f): List<Detection> {
    if (candidates.size <= 1) return candidates
    val sorted = candidates.sortedByDescending { it.score }
    val kept = ArrayList<Detection>()
    val suppressed = BooleanArray(sorted.size)
    for (i in sorted.indices) {
        if (suppressed[i]) continue
        val a = sorted[i]
        kept.add(a)
        for (j in i + 1 until sorted.size) {
            if (suppressed[j]) continue
            val b = sorted[j]
            if (a.classIndex == b.classIndex && iou(a.box, b.box) > iouThreshold) {
                suppressed[j] = true
            }
        }
    }
    return kept
}
