package com.example.cvproject.domain.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

 
class RemoteYoloService(
    private val serverUrl: String,
) {
    companion object {
        private const val TAG = "RemoteYolo"
        private const val JPEG_QUALITY = 60
         
        private const val CONNECT_TIMEOUT_MS = 60000
        private const val READ_TIMEOUT_MS = 60000
        private const val BOUNDARY = "----LogiVisionBoundary"
        private const val MAX_DIMENSION = 640
    }

    data class DetectionResult(
        val boxes: List<RectF>,
        val trackIds: List<Int>,
        val imageWidth: Int,
        val imageHeight: Int,
    )

    
    fun detect(bitmap: Bitmap, rotationDegrees: Int = 0): DetectionResult {
        val startMs = System.currentTimeMillis()

         
        val rotated = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }

        
        val scaled = downscale(rotated, MAX_DIMENSION)
        if (rotated !== bitmap && rotated !== scaled) {
            rotated.recycle() 
        }

        Log.d(TAG, "Prepared ${bitmap.width}x${bitmap.height} rot=$rotationDegrees -> ${scaled.width}x${scaled.height}")

        
        val jpegBytes = ByteArrayOutputStream().use { stream ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            stream.toByteArray()
        }
        if (scaled !== bitmap && scaled !== rotated) {
            scaled.recycle()
        }
        Log.d(TAG, "JPEG: ${jpegBytes.size} bytes")

        
        val url = URL("$serverUrl/detect")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$BOUNDARY")
        }

        try {
            
            connection.outputStream.use { out ->
                val header = "--$BOUNDARY\r\n" +
                    "Content-Disposition: form-data; name=\"image\"; filename=\"frame.jpg\"\r\n" +
                    "Content-Type: image/jpeg\r\n\r\n"
                out.write(header.toByteArray())
                out.write(jpegBytes)
                val footer = "\r\n--$BOUNDARY--\r\n"
                out.write(footer.toByteArray())
                out.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "unknown"
                Log.e(TAG, "Server returned $responseCode: $errorBody")
                return DetectionResult(emptyList(), emptyList(), scaled.width, scaled.height)
            }

            
            val responseBody = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(responseBody)
            val detectionsArray = json.getJSONArray("detections")
            val imageWidth = json.optInt("image_width", scaled.width)
            val imageHeight = json.optInt("image_height", scaled.height)

            val boxes = mutableListOf<RectF>()
            val trackIds = mutableListOf<Int>()
            for (i in 0 until detectionsArray.length()) {
                val detection = detectionsArray.getJSONObject(i)
                val boxArray = detection.getJSONArray("box")
                
                val left = boxArray.getDouble(0).toFloat()
                val top = boxArray.getDouble(1).toFloat()
                val right = boxArray.getDouble(2).toFloat()
                val bottom = boxArray.getDouble(3).toFloat()
                boxes.add(RectF(left, top, right, bottom))

                
                val trackId = detection.optInt("track_id", -1)
                trackIds.add(trackId)

                val className = detection.optString("class", "vehicle")
                val confidence = detection.optDouble("confidence", 0.0)
                Log.d(TAG, "  $className #$trackId (${String.format("%.2f", confidence)}): [$left, $top, $right, $bottom]")
            }

            val elapsedMs = System.currentTimeMillis() - startMs
            Log.d(TAG, "Detection complete: ${boxes.size} vehicles in ${elapsedMs}ms")

            return DetectionResult(boxes, trackIds, imageWidth, imageHeight)
        } finally {
            connection.disconnect()
        }
    }

   
    private fun downscale(bitmap: Bitmap, maxDim: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDim) return bitmap
        val scale = maxDim.toFloat() / maxSide
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
