package com.esmer.queenfinder.ui.camera

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esmer.queenfinder.detection.DetectionState
import com.esmer.queenfinder.ui.theme.DroneBox
import com.esmer.queenfinder.ui.theme.QueenBox
import kotlin.math.max

/**
 * Draws detection boxes over a PreviewView that uses FILL_CENTER scaling.
 * The analysed frame and the preview share the same aspect ratio, so a box in
 * normalized frame coordinates maps to the view with one scale and a centre offset.
 */
@Composable
fun DetectionOverlay(
    state: DetectionState,
    showDrones: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val strokeThin = with(density) { 2.dp.toPx() }
    val strokeThick = with(density) { 4.dp.toPx() }
    val textSize = with(density) { 14.sp.toPx() }

    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = textSize
        typeface = Typeface.DEFAULT_BOLD
        color = Color.Black.toArgb()
    }
    val labelBg = Paint(Paint.ANTI_ALIAS_FLAG)

    Canvas(modifier = modifier) {
        if (state.frameWidth == 0 || state.frameHeight == 0) return@Canvas
        val scale = max(size.width / state.frameWidth, size.height / state.frameHeight)
        val dx = (size.width - state.frameWidth * scale) / 2f
        val dy = (size.height - state.frameHeight * scale) / 2f

        fun mapX(nx: Float) = nx * state.frameWidth * scale + dx
        fun mapY(ny: Float) = ny * state.frameHeight * scale + dy

        for (d in state.detections) {
            val color = when {
                d.isQueen -> QueenBox
                d.isDrone && showDrones -> DroneBox
                else -> continue
            }
            val l = mapX(d.box.left); val t = mapY(d.box.top)
            val r = mapX(d.box.right); val b = mapY(d.box.bottom)
            drawRect(
                color = color,
                topLeft = Offset(l, t),
                size = Size(r - l, b - t),
                style = Stroke(width = if (d.isQueen) strokeThick else strokeThin),
            )
            val text = "${d.label} ${(d.score * 100).toInt()}%"
            val tw = labelPaint.measureText(text)
            labelBg.color = color.toArgb()
            drawContext.canvas.nativeCanvas.apply {
                drawRect(l, t - textSize - 8f, l + tw + 12f, t, labelBg)
                drawText(text, l + 6f, t - 6f, labelPaint)
            }
        }

        state.stableQueen?.let { q ->
            val cx = mapX(q.box.centerX)
            val cy = mapY(q.box.centerY)
            val radius = max(mapX(q.box.right) - mapX(q.box.left), mapY(q.box.bottom) - mapY(q.box.top)) * 0.9f
            drawCircle(
                color = QueenBox,
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = strokeThick),
            )
        }
    }
}
