package com.example.cvproject.domain.camera

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

 
class YoloImageAnalyzer(
    private val onFrameAnalyzed: (Bitmap) -> Unit,
) : ImageAnalysis.Analyzer {

    private var frameCounter = 0

    override fun analyze(imageProxy: ImageProxy) {
        try {
            
            frameCounter++
            if (frameCounter % 3 != 0) return

            val bitmap = imageProxy.toBitmap() ?: return
            val prepared = prepareForYolo(bitmap)
            onFrameAnalyzed(prepared)
        } finally {
            imageProxy.close()
        }
    }

    
    private fun ImageProxy.toBitmap(): Bitmap? {
        return try {
            val yuvBytes = planes
            if (yuvBytes.isEmpty()) return null

        
            val yBuffer = yuvBytes[0].buffer
            val uBuffer = yuvBytes[1].buffer
            val vBuffer = yuvBytes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = android.graphics.YuvImage(
                nv21,
                ImageFormat.NV21,
                width,
                height,
                null
            )
            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 90, out)
            val jpegBytes = out.toByteArray()
            val decoded = android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                ?: return null

         
            val matrix = Matrix().apply { postRotate(imageInfo.rotationDegrees.toFloat()) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } catch (e: Exception) {
            null
        }
    }

    
    private fun prepareForYolo(source: Bitmap): Bitmap {
        val targetSize = 640
        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        canvas.drawColor(android.graphics.Color.BLACK)

        val scale = minOf(
            targetSize.toFloat() / source.width,
            targetSize.toFloat() / source.height
        )
        val scaledW = (source.width * scale).toInt()
        val scaledH = (source.height * scale).toInt()
        val offsetX = (targetSize - scaledW) / 2
        val offsetY = (targetSize - scaledH) / 2

        val scaled = Bitmap.createScaledBitmap(source, scaledW, scaledH, true)
        canvas.drawBitmap(scaled, offsetX.toFloat(), offsetY.toFloat(), null)
        return output
    }
}
