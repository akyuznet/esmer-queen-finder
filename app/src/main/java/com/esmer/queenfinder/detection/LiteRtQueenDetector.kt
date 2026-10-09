package com.esmer.queenfinder.detection

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * YOLO detector on the LiteRT (TensorFlow Lite) interpreter.
 *
 * Accepts the three output layouts Ultralytics produces:
 *  - YOLOv8/11/26: [1, 4 + nc, N] or [1, N, 4 + nc], boxes xywh normalized
 *  - YOLOv5:       [1, N, 5 + nc] with an objectness column
 *
 * The GPU delegate must be created and used on the same thread, so [load] and
 * [detect] are both expected on the inference executor.
 */
class LiteRtQueenDetector(
    private val context: Context,
    override val modelName: String = DEFAULT_MODEL,
    private val labelsFile: String = DEFAULT_LABELS,
    private val preferGpu: Boolean = true,
    private val threads: Int = 4,
) : QueenDetector {

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var preprocessor: FramePreprocessor? = null
    private var outputBuffer: ByteBuffer? = null
    private var outputFloats: FloatArray = FloatArray(0)

    override var inputSize: Int = 0
        private set
    override var backend: Backend = Backend.NONE
        private set
    override var labels: List<String> = emptyList()
        private set

    private var inputType: DataType = DataType.FLOAT32
    private var channelsFirst = false
    private var inScale = 1f
    private var inZero = 0
    private var outType: DataType = DataType.FLOAT32
    private var outScale = 1f
    private var outZero = 0
    private var outShape = intArrayOf()
    private lateinit var layout: Layout

    private enum class Layout { V8_CHANNELS_FIRST, V8_CHANNELS_LAST, V5 }

    override fun load() {
        if (interpreter != null) return
        labels = context.assets.open(labelsFile).bufferedReader().readLines()
            .map { it.trim() }.filter { it.isNotEmpty() }
        val model = mapModel(modelName)

        var created: Interpreter? = null
        if (preferGpu) {
            try {
                val compat = CompatibilityList()
                if (compat.isDelegateSupportedOnThisDevice) {
                    val delegate = GpuDelegate(compat.bestOptionsForThisDevice)
                    val options = Interpreter.Options().addDelegate(delegate)
                    created = Interpreter(model, options)
                    gpuDelegate = delegate
                    backend = Backend.GPU
                }
            } catch (t: Throwable) {
                Log.w(TAG, "GPU delegate failed, falling back to CPU", t)
                gpuDelegate?.close()
                gpuDelegate = null
                created = null
            }
        }
        if (created == null) {
            val options = Interpreter.Options().setNumThreads(threads).setUseXNNPACK(true)
            created = Interpreter(model, options)
            backend = Backend.CPU
        }
        interpreter = created

        val input = created.getInputTensor(0)
        val shape = input.shape() // [1, H, W, 3] (NHWC) or [1, 3, H, W] (NCHW)
        channelsFirst = shape.size == 4 && shape[1] == 3 && shape[3] != 3
        inputSize = if (channelsFirst) shape[2] else shape[1]
        inputType = input.dataType()
        inScale = input.quantizationParams().scale
        inZero = input.quantizationParams().zeroPoint
        preprocessor = FramePreprocessor(inputSize)

        val output = created.getOutputTensor(0)
        outShape = output.shape()
        outType = output.dataType()
        outScale = output.quantizationParams().scale
        outZero = output.quantizationParams().zeroPoint
        val nc = labels.size
        layout = when {
            outShape.size == 3 && outShape[1] == 4 + nc -> Layout.V8_CHANNELS_FIRST
            outShape.size == 3 && outShape[2] == 4 + nc -> Layout.V8_CHANNELS_LAST
            outShape.size == 3 && outShape[2] == 5 + nc -> Layout.V5
            else -> throw IllegalStateException(
                "Unsupported output shape ${outShape.toList()} for ${labels.size} labels"
            )
        }
        val count = outShape.fold(1) { acc, d -> acc * d }
        outputFloats = FloatArray(count)
        outputBuffer = ByteBuffer.allocateDirect(output.numBytes()).order(ByteOrder.nativeOrder())
        Log.i(TAG, "Loaded $modelName on $backend, input $inputSize ${if (channelsFirst) "NCHW" else "NHWC"} $inputType, output ${outShape.toList()}, layout $layout")
    }

    override fun detect(frame: Bitmap, confidence: Float): List<Detection> {
        val interp = interpreter ?: error("call load() first")
        val pre = preprocessor!!
        val lb = pre.letterbox(frame)
        val input: ByteBuffer = when (inputType) {
            DataType.FLOAT32 -> pre.fillFloat(channelsFirst)
            DataType.UINT8 -> pre.fillQuantized(inScale, inZero, signed = false, channelsFirst = channelsFirst)
            DataType.INT8 -> pre.fillQuantized(inScale, inZero, signed = true, channelsFirst = channelsFirst)
            else -> error("Unsupported input type $inputType")
        }
        val out = outputBuffer!!
        out.rewind()
        interp.run(input, out)
        out.rewind()
        readOutput(out)
        return nms(decode(confidence, lb))
    }

    private fun readOutput(buf: ByteBuffer) {
        when (outType) {
            DataType.FLOAT32 -> buf.asFloatBuffer().get(outputFloats)
            DataType.INT8 -> for (i in outputFloats.indices) {
                outputFloats[i] = (buf.get().toInt() - outZero) * outScale
            }
            DataType.UINT8 -> for (i in outputFloats.indices) {
                outputFloats[i] = ((buf.get().toInt() and 0xFF) - outZero) * outScale
            }
            else -> error("Unsupported output type $outType")
        }
    }

    private fun decode(confidence: Float, lb: Letterbox): List<Detection> {
        val nc = labels.size
        val out = outputFloats
        val result = ArrayList<Detection>()
        val n: Int
        val stride: Int
        when (layout) {
            Layout.V8_CHANNELS_FIRST -> { n = outShape[2]; stride = 0 }
            Layout.V8_CHANNELS_LAST -> { n = outShape[1]; stride = 4 + nc }
            Layout.V5 -> { n = outShape[1]; stride = 5 + nc }
        }
        // Detect whether coordinates are already normalized or in input pixels.
        var pixelSpace = false
        for (i in 0 until minOf(n, 64)) {
            if (value(out, i, 0, n, stride) > 1.5f || value(out, i, 1, n, stride) > 1.5f) {
                pixelSpace = true; break
            }
        }
        val norm = if (pixelSpace) 1f / lb.inputSize else 1f

        for (i in 0 until n) {
            var best = -1
            var bestScore = 0f
            val obj = if (layout == Layout.V5) value(out, i, 4, n, stride) else 1f
            if (obj < confidence) continue
            val classOffset = if (layout == Layout.V5) 5 else 4
            for (c in 0 until nc) {
                val s = value(out, i, classOffset + c, n, stride) * obj
                if (s > bestScore) { bestScore = s; best = c }
            }
            if (best < 0 || bestScore < confidence) continue
            val cx = value(out, i, 0, n, stride) * norm
            val cy = value(out, i, 1, n, stride) * norm
            val w = value(out, i, 2, n, stride) * norm
            val h = value(out, i, 3, n, stride) * norm
            val box = Box(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
            result.add(Detection(best, labels[best], bestScore, lb.toFrame(box)))
        }
        return result
    }

    /** Reads channel [c] of anchor [i] for either output layout. */
    private fun value(out: FloatArray, i: Int, c: Int, n: Int, stride: Int): Float =
        if (layout == Layout.V8_CHANNELS_FIRST) out[c * n + i] else out[i * stride + c]

    private fun mapModel(name: String): MappedByteBuffer {
        context.assets.openFd(name).use { fd ->
            FileInputStream(fd.fileDescriptor).use { stream ->
                return stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
        backend = Backend.NONE
    }

    companion object {
        private const val TAG = "LiteRtQueenDetector"
        const val DEFAULT_MODEL = "queen.tflite"
        const val DEFAULT_LABELS = "labels.txt"
    }
}
