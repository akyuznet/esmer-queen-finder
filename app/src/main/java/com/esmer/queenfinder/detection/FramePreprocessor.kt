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

/** How a frame was placed inside the model input. */
class Letterbox(
    val inputWidth: Int,
    val inputHeight: Int,
    val scale: Float,
    val padX: Float,
    val padY: Float,
    val frameWidth: Int,
    val frameHeight: Int,
) {
    /** Map a box from normalized model-input space (0..1 of the input) to normalized frame space. */
    fun toFrame(box: Box): Box {
        val l = ((box.left * inputWidth - padX) / scale / frameWidth).coerceIn(0f, 1f)
        val t = ((box.top * inputHeight - padY) / scale / frameHeight).coerceIn(0f, 1f)
        val r = ((box.right * inputWidth - padX) / scale / frameWidth).coerceIn(0f, 1f)
        val b = ((box.bottom * inputHeight - padY) / scale / frameHeight).coerceIn(0f, 1f)
        return Box(l, t, r, b)
    }
}

/**
 * Turns an RGB bitmap into the model's input tensor with letterbox padding,
 * reusing buffers between frames. The input may be rectangular; it is fed the
 * frame in its sensor orientation so a 16:9 model wastes no pixels on padding.
 */
class FramePreprocessor(val inputWidth: Int, val inputHeight: Int) {

    private val canvasBitmap: Bitmap = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(canvasBitmap)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pixels = IntArray(inputWidth * inputHeight)
    private val count = inputWidth * inputHeight

    /** Float32 input, RGB in 0..1. */
    val floatBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(4 * count * 3)
        .order(ByteOrder.nativeOrder())

    /** Quantized input, one byte per channel. */
    val byteBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(count * 3)
        .order(ByteOrder.nativeOrder())

    /** Draws [frame] letterboxed into the input and returns the placement. */
    fun letterbox(frame: Bitmap): Letterbox {
        val scale = min(inputWidth.toFloat() / frame.width, inputHeight.toFloat() / frame.height)
        val newW = (frame.width * scale).roundToInt()
        val newH = (frame.height * scale).roundToInt()
        val padX = (inputWidth - newW) / 2f
        val padY = (inputHeight - newH) / 2f

        canvas.drawColor(Color.rgb(114, 114, 114))
        val m = Matrix()
        m.postScale(scale, scale)
        m.postTranslate(padX, padY)
        canvas.drawBitmap(frame, m, paint)
        canvasBitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        return Letterbox(inputWidth, inputHeight, scale, padX, padY, frame.width, frame.height)
    }

    private val floats = FloatArray(count * 3)

    /**
     * Fills [floatBuffer] from the last letterboxed input.
     * @param channelsFirst true for NCHW inputs (planar R, G, B), false for NHWC.
     */
    fun fillFloat(channelsFirst: Boolean = false): ByteBuffer {
        val lut = LUT
        val f = floats
        val px = pixels
        if (channelsFirst) {
            var j = 0
            for (p in px) f[j++] = lut[(p shr 16) and 0xFF]
            for (p in px) f[j++] = lut[(p shr 8) and 0xFF]
            for (p in px) f[j++] = lut[p and 0xFF]
        } else {
            var j = 0
            for (p in px) {
                f[j] = lut[(p shr 16) and 0xFF]
                f[j + 1] = lut[(p shr 8) and 0xFF]
                f[j + 2] = lut[p and 0xFF]
                j += 3
            }
        }
        floatBuffer.rewind()
        floatBuffer.asFloatBuffer().put(f)
        floatBuffer.rewind()
        return floatBuffer
    }

    /**
     * Fills [byteBuffer] from the last letterboxed input, quantizing each 0..1 value
     * with the input tensor's scale and zero point.
     */
    fun fillQuantized(scale: Float, zeroPoint: Int, signed: Boolean, channelsFirst: Boolean = false): ByteBuffer {
        byteBuffer.rewind()
        val lo = if (signed) -128 else 0
        val hi = if (signed) 127 else 255
        val inv = if (scale == 0f) 255f else 1f / (255f * scale)
        if (channelsFirst) {
            for (p in pixels) byteBuffer.put(q(((p shr 16) and 0xFF), inv, zeroPoint, lo, hi))
            for (p in pixels) byteBuffer.put(q(((p shr 8) and 0xFF), inv, zeroPoint, lo, hi))
            for (p in pixels) byteBuffer.put(q((p and 0xFF), inv, zeroPoint, lo, hi))
        } else {
            for (p in pixels) {
                byteBuffer.put(q(((p shr 16) and 0xFF), inv, zeroPoint, lo, hi))
                byteBuffer.put(q(((p shr 8) and 0xFF), inv, zeroPoint, lo, hi))
                byteBuffer.put(q((p and 0xFF), inv, zeroPoint, lo, hi))
            }
        }
        byteBuffer.rewind()
        return byteBuffer
    }

    private fun q(v: Int, inv: Float, zero: Int, lo: Int, hi: Int): Byte =
        (v * inv + zero).roundToInt().coerceIn(lo, hi).toByte()

    companion object {
        private val LUT = FloatArray(256) { it / 255f }

        /** Rotates [src] by [degrees] clockwise so the result is upright. */
        fun upright(src: Bitmap, degrees: Int): Bitmap {
            if (degrees % 360 == 0) return src
            val m = Matrix().apply { postRotate(degrees.toFloat()) }
            return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        }
    }
}
