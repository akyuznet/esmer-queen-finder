package com.esmer.queenfinder.detection

/**
 * Rotates a normalized box from the sensor frame into the upright frame.
 *
 * @param degrees the clockwise rotation (0, 90, 180, 270) that makes the sensor
 *   frame upright, as reported by CameraX's imageInfo.rotationDegrees.
 */
fun rotateBox(box: Box, degrees: Int): Box = when (((degrees % 360) + 360) % 360) {
    90 -> Box(1f - box.bottom, box.left, 1f - box.top, box.right)
    180 -> Box(1f - box.right, 1f - box.bottom, 1f - box.left, 1f - box.top)
    270 -> Box(box.top, 1f - box.right, box.bottom, 1f - box.left)
    else -> box
}

fun rotateDetection(d: Detection, degrees: Int): Detection =
    if (degrees % 360 == 0) d else d.copy(box = rotateBox(d.box, degrees))
