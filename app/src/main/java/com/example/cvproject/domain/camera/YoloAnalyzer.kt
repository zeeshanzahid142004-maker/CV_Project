package com.example.cvproject.domain.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.cvproject.BuildConfig
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.Locale

private const val NMS_IOU_THRESHOLD = 0.30f

private const val MAX_DETECTIONS = 20

class YoloAnalyzer(
    context: Context,
    private val onResults: (List<RectF>, Int, Int) -> Unit,
) : ImageAnalysis.Analyzer {

    private val interpreter: Interpreter?
    private val gpuDelegate: org.tensorflow.lite.gpu.GpuDelegate?
    val modelLoaded: Boolean
        get() = interpreter != null
    private var hasLoggedOutputShape = false
    private var frameCounter = 0L

    init {
        var createdGpuDelegate: org.tensorflow.lite.gpu.GpuDelegate? = null
        val options = Interpreter.Options().apply {
            setNumThreads(4)
            try {
                createdGpuDelegate = org.tensorflow.lite.gpu.GpuDelegate()
                addDelegate(createdGpuDelegate)
            } catch (e: Exception) {
                Log.w("YoloAnalyzer", "GPU delegate not supported, falling back to CPU: ${e.message}")
            }
        }
        gpuDelegate = createdGpuDelegate
        val modelBuffer = loadModelFile(context, "yolo11s.tflite")
        interpreter = modelBuffer?.let { Interpreter(it, options) }
    }

    override fun analyze(imageProxy: ImageProxy) {
        try {
            frameCounter++
            val frameId = frameCounter
            val shouldLogDetailed = BuildConfig.DEBUG && frameId % DEBUG_LOG_EVERY_N_FRAMES == 0L
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "Frame #$frameId start rotation=${imageProxy.imageInfo.rotationDegrees} size=${imageProxy.width}x${imageProxy.height}",
                )
            }
            if (!modelLoaded) {
                Log.d(TAG, "Frame #$frameId skipped: model not loaded")
                onResults(emptyList(), imageProxy.width, imageProxy.height)
                return
            }

            val bitmap = imageProxy.toBitmap()
            if (bitmap == null) {
                Log.d(TAG, "Frame #$frameId failed: YUV->RGB conversion returned null")
                onResults(emptyList(), imageProxy.width, imageProxy.height)
                return
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Frame #$frameId bitmap size=${bitmap.width}x${bitmap.height}")
            }

            val bw = bitmap.width.toFloat()
            val bh = bitmap.height.toFloat()
            val scale = minOf(MODEL_INPUT_SIZE / bw, MODEL_INPUT_SIZE / bh)
            val scaledW = (bw * scale).toInt()
            val scaledH = (bh * scale).toInt()
            val padX = (MODEL_INPUT_SIZE - scaledW) / 2
            val padY = (MODEL_INPUT_SIZE - scaledH) / 2

            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
            val resized = Bitmap.createBitmap(MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(resized)
            canvas.drawColor(android.graphics.Color.BLACK)
            canvas.drawBitmap(scaledBitmap, padX.toFloat(), padY.toFloat(), null)

            if (shouldLogDetailed) {
                Log.d(TAG, "Frame #$frameId letterboxed ${bitmap.width}x${bitmap.height} -> ${MODEL_INPUT_SIZE}x${MODEL_INPUT_SIZE} pad=($padX,$padY)")
            }
            val input = bitmapToBuffer(resized)
            val output = Array(1) { Array(OUTPUT_ROWS) { FloatArray(OUTPUT_COLS) } }
            val inferenceStartNs = System.nanoTime()
            interpreter?.run(input, output)
            val inferenceMs = (System.nanoTime() - inferenceStartNs) / 1_000_000f
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Frame #$frameId inference finished in ${formatMs(inferenceMs)}ms")
            }

            if (!hasLoggedOutputShape) {
                interpreter?.getOutputTensor(0)?.let { outputTensor ->
                    Log.d(TAG, "Output shape: ${outputTensor.shape().contentToString()}")
                }
                hasLoggedOutputShape = true
            }

            val parseStartNs = System.nanoTime()
            val parseResult = parseDetections(
                output = output[0],
                imageWidth = MODEL_INPUT_SIZE.toFloat(),
                imageHeight = MODEL_INPUT_SIZE.toFloat(),
                frameId = frameId,
                logDetailed = shouldLogDetailed,
            )
            val parseMs = (System.nanoTime() - parseStartNs) / 1_000_000f

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Frame #$frameId parse finished in ${formatMs(parseMs)}ms")
                Log.d(TAG, "Frame #$frameId raw candidates above threshold: ${parseResult.rawCandidates.size}")
                Log.d(TAG, "Frame #$frameId max score seen: ${parseResult.maxScoreSeen}")
                Log.d(
                    TAG,
                    "Frame #$frameId candidates above ${parseResult.debugThreshold}: ${parseResult.lowThresholdCandidateCount}",
                )
                Log.d(
                    TAG,
                    "Frame #$frameId filtered out by confidence=${parseResult.filteredByConfidenceCount}, class=${parseResult.filteredByClassCount}",
                )
            }

            val nmsStartNs = System.nanoTime()
            val results = nms(
                boxes = parseResult.rawCandidates.map { it.rect },
                scores = parseResult.rawCandidates.map { it.confidence },
            )
            val nmsMs = (System.nanoTime() - nmsStartNs) / 1_000_000f

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Frame #$frameId NMS finished in ${formatMs(nmsMs)}ms")
                Log.d(TAG, "Frame #$frameId detections this frame: ${results.size}")
            }

            val mapped = results.map { det ->
                RectF(
                    ((det.left - padX) / scaledW.toFloat()).coerceIn(0f, 1f),
                    ((det.top - padY) / scaledH.toFloat()).coerceIn(0f, 1f),
                    ((det.right - padX) / scaledW.toFloat()).coerceIn(0f, 1f),
                    ((det.bottom - padY) / scaledH.toFloat()).coerceIn(0f, 1f)
                )
            }
            if (shouldLogDetailed) {
                Log.d(TAG, "Frame #$frameId returning ${mapped.size} boxes as 0..1 fractions")
            }
            onResults(mapped, bitmap.width, bitmap.height)
        } finally {
            imageProxy.close()
        }
    }

    fun close() {
        interpreter?.close()
        gpuDelegate?.close()
    }

    private fun parseDetections(
        output: Array<FloatArray>,
        imageWidth: Float,
        imageHeight: Float,
        frameId: Long,
        logDetailed: Boolean,
    ): ParseDetectionsResult {
        val rawCandidates = mutableListOf<Detection>()
        var maxScoreSeen = 0f
        var topFilteredClass = -1
        var topFilteredScore = 0f

        val debugThreshold = LOW_THRESHOLD_DEBUG
        var lowThresholdCandidateCount = 0
        var filteredByConfidenceCount = 0
        var filteredByClassCount = 0
        var detailedClassLogCount = 0

        for (col in 0 until OUTPUT_COLS) {
            val cx = output[0][col]
            val cy = output[1][col]
            val w  = output[2][col]
            val h  = output[3][col]

            var bestClass = -1
            var bestRawScore = Float.NEGATIVE_INFINITY
            for (classId in 0 until COCO_CLASS_COUNT) {
                val rawScore = output[4 + classId][col]
                if (rawScore > bestRawScore) {
                    bestRawScore = rawScore
                    bestClass = classId
                }
            }
            val bestScore = sigmoid(bestRawScore)

            val shouldLogClassCandidate = col == 0 || bestScore >= CLASS_DEBUG_LOG_THRESHOLD
            if (logDetailed && shouldLogClassCandidate && detailedClassLogCount < MAX_DETAILED_CLASS_LOGS_PER_FRAME) {
                Log.d(
                    TAG,
                    "Frame #$frameId col=$col bestClass=$bestClass bestScore=$bestScore box=($cx,$cy,$w,$h)",
                )
                detailedClassLogCount++
            }

            if (bestScore > maxScoreSeen) maxScoreSeen = bestScore
            if (bestScore >= debugThreshold) lowThresholdCandidateCount++

            if (bestScore < CONFIDENCE_THRESHOLD) {
                filteredByConfidenceCount++
                continue
            }
            if (bestClass !in VEHICLE_CLASSES) {
                if (bestScore > topFilteredScore) {
                    topFilteredClass = bestClass
                    topFilteredScore = bestScore
                }
                filteredByClassCount++
                continue
            }

            val left   = ((cx - w / 2f) * imageWidth).coerceIn(0f, imageWidth)
            val top    = ((cy - h / 2f) * imageHeight).coerceIn(0f, imageHeight)
            val right  = ((cx + w / 2f) * imageWidth).coerceIn(0f, imageWidth)
            val bottom = ((cy + h / 2f) * imageHeight).coerceIn(0f, imageHeight)

            rawCandidates += Detection(RectF(left, top, right, bottom), bestScore)
        }

        if (logDetailed) {
            Log.d(
                TAG,
                "Frame #$frameId class logs emitted=$detailedClassLogCount/$MAX_DETAILED_CLASS_LOGS_PER_FRAME",
            )
        }
        if (BuildConfig.DEBUG && rawCandidates.isEmpty() && topFilteredScore > CONFIDENCE_THRESHOLD) {
            Log.d(
                TAG,
                "High confidence candidate filtered by class. Top bestClass this frame: $topFilteredClass score: $topFilteredScore",
            )
        }

        return ParseDetectionsResult(
            rawCandidates = rawCandidates,
            maxScoreSeen = maxScoreSeen,
            lowThresholdCandidateCount = lowThresholdCandidateCount,
            filteredByConfidenceCount = filteredByConfidenceCount,
            filteredByClassCount = filteredByClassCount,
            debugThreshold = debugThreshold,
        )
    }

    private fun nms(boxes: List<RectF>, scores: List<Float>): List<RectF> {
        if (boxes.isEmpty() || scores.isEmpty()) return emptyList()
        require(boxes.size == scores.size) {
            "NMS input size mismatch: boxes=${boxes.size}, scores=${scores.size}"
        }
        val sortedIndices = boxes.indices.sortedByDescending { scores[it] }
        val kept = mutableListOf<RectF>()
        for (index in sortedIndices) {
            val candidate = boxes[index]
            val overlapsKept = kept.any { iou(candidate, it) >= NMS_IOU_THRESHOLD }
            if (!overlapsKept) {
                kept += candidate
                if (kept.size >= MAX_DETECTIONS) break
            }
        }
        return kept
    }

    private fun iou(a: RectF, b: RectF): Float {
        val interLeft   = maxOf(a.left,   b.left)
        val interTop    = maxOf(a.top,    b.top)
        val interRight  = minOf(a.right,  b.right)
        val interBottom = minOf(a.bottom, b.bottom)
        val interArea = maxOf(0f, interRight - interLeft) * maxOf(0f, interBottom - interTop)
        if (interArea <= 0f) return 0f
        val union = a.width() * a.height() + b.width() * b.height() - interArea
        return if (union <= 0f) 0f else interArea / union
    }

    private fun bitmapToBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer
            .allocateDirect(1 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * 3 * 4)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        bitmap.getPixels(pixels, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)

        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
        }
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
        }
        for (pixel in pixels) {
            buffer.putFloat((pixel and 0xFF) / 255f)
        }

        buffer.rewind()
        return buffer
    }

    private fun loadModelFile(context: Context, assetName: String): ByteBuffer? {
        return try {
            val afd = context.assets.openFd(assetName)
            FileInputStream(afd.fileDescriptor).use { input ->
                input.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
            }
        } catch (e: IOException) {
            Log.e(
                "YoloAnalyzer",
                "yolo11s.tflite not found in assets. Place the model file at app/src/main/assets/yolo11s.tflite",
                e,
            )
            null
        }
    }

    private fun ImageProxy.toBitmap(): Bitmap? {
        return try {
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val stream = ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 90, stream)
            val bytes = stream.toByteArray()
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

            val matrix = Matrix().apply { postRotate(imageInfo.rotationDegrees.toFloat()) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } catch (_: Exception) {
            null
        }
    }

    private data class Detection(val rect: RectF, val confidence: Float)

    private fun formatMs(valueMs: Float): String = String.format(Locale.US, "%.2f", valueMs)

    private fun sigmoid(value: Float): Float = (1f / (1f + Math.exp(-value.toDouble()))).toFloat()

    private data class ParseDetectionsResult(
        val rawCandidates: List<Detection>,
        val maxScoreSeen: Float,
        val lowThresholdCandidateCount: Int,
        val filteredByConfidenceCount: Int,
        val filteredByClassCount: Int,
        val debugThreshold: Float,
    )

    companion object {
        private const val TAG = "YoloAnalyzer"
        private const val DEBUG_LOG_EVERY_N_FRAMES = 10
        private const val CLASS_DEBUG_LOG_THRESHOLD = 0.30f
        private const val MAX_DETAILED_CLASS_LOGS_PER_FRAME = 25
        private const val CONFIDENCE_THRESHOLD = 0.36f
        private const val LOW_THRESHOLD_DEBUG = 0.01f
        private val VEHICLE_CLASSES = setOf(2, 3, 5, 6, 7, 8)
        private const val MODEL_INPUT_SIZE = 640
        private const val OUTPUT_ROWS = 84
        private const val OUTPUT_COLS = 8400
        private const val COCO_CLASS_COUNT = 80
    }
}
