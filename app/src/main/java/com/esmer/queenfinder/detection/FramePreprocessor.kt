package com.esmer.queenfinder.detection

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.roundToInt

/** How an upright frame was placed inside the square model input. */
class Letterbox(
    val inputSize: Int,
    val scale: Float,
    val padX: Float,
    val padY: Float,
    val frameWidth: Int,
    val frameHeight: Int,
) {
    /** Map a box from normalized model-input space (0..1 of the square) to normalized frame space. */
    fun toFrame(box: Box): Box {
        val l = ((box.left * inputSize - padX) / scale / frameWidth).coerceIn(0f, 1f)
        val t = ((box.top * inputSize - padY) / scale / frameHeight).coerceIn(0f, 1f)
        val r = ((box.right * inputSize - padX) / scale / frameWidth).coerceIn(0f, 1f)
        val b = ((box.bottom * inputSize - padY) / scale / frameHeight).coerceIn(0f, 1f)
        return Box(l, t, r, b)
    }
}

/**
 * Turns an upright RGB bitmap into the model's square input tensor with letterbox
 * padding, reusing buffers between frames.
 */
class FramePreprocessor(private val inputSize: Int) {

    private val square: Bitmap = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(square)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pixels = IntArray(inputSize * inputSize)

    /** Float32 input, RGB in 0..1, NHWC. */
    val floatBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(4 * inputSize * inputSize * 3)
        .order(ByteOrder.nativeOrder())

    /** Quantized input, one byte per channel, NHWC. */
    val byteBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(inputSize * inputSize * 3)
        .order(ByteOrder.nativeOrder())

    /** Draws [frame] letterboxed into the square and returns the placement. */
    fun letterbox(frame: Bitmap): Letterbox {
        val scale = min(inputSize.toFloat() / frame.width, inputSize.toFloat() / frame.height)
        val newW = (frame.width * scale).roundToInt()
        val newH = (frame.height * scale).roundToInt()
        val padX = (inputSize - newW) / 2f
        val padY = (inputSize - newH) / 2f

        canvas.drawColor(Color.rgb(114, 114, 114))
        val m = Matrix()
        m.postScale(scale, scale)
        m.postTranslate(padX, padY)
        canvas.drawBitmap(frame, m, paint)
        square.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        return Letterbox(inputSize, scale, padX, padY, frame.width, frame.height)
    }

    /** Fills [floatBuffer] from the last letterboxed square. */
    fun fillFloat(): ByteBuffer {
        floatBuffer.rewind()
        val fb = floatBuffer.asFloatBuffer()
        for (p in pixels) {
            fb.put(((p shr 16) and 0xFF) / 255f)
            fb.put(((p shr 8) and 0xFF) / 255f)
            fb.put((p and 0xFF) / 255f)
        }
        floatBuffer.rewind()
        return floatBuffer
    }

    /**
     * Fills [byteBuffer] from the last letterboxed square, quantizing each 0..1 value
     * with the input tensor's scale and zero point.
     */
    fun fillQuantized(scale: Float, zeroPoint: Int, signed: Boolean): ByteBuffer {
        byteBuffer.rewind()
        val lo = if (signed) -128 else 0
        val hi = if (signed) 127 else 255
        val inv = if (scale == 0f) 255f else 1f / (255f * scale)
        for (p in pixels) {
            byteBuffer.put(q(((p shr 16) and 0xFF), inv, zeroPoint, lo, hi))
            byteBuffer.put(q(((p shr 8) and 0xFF), inv, zeroPoint, lo, hi))
            byteBuffer.put(q((p and 0xFF), inv, zeroPoint, lo, hi))
        }
        byteBuffer.rewind()
        return byteBuffer
    }

    private fun q(v: Int, inv: Float, zero: Int, lo: Int, hi: Int): Byte =
        (v * inv + zero).roundToInt().coerceIn(lo, hi).toByte()

    companion object {
        /** Rotates [src] by [degrees] clockwise so the result is upright. */
        fun upright(src: Bitmap, degrees: Int): Bitmap {
            if (degrees % 360 == 0) return src
            val m = Matrix().apply { postRotate(degrees.toFloat()) }
            return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        }
    }
}
