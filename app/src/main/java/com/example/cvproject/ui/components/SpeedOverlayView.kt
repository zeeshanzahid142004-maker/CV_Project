package com.example.cvproject.ui.components

import android.graphics.RectF
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.example.cvproject.domain.camera.TrackedVehicle

private const val MODEL_REFERENCE_WIDTH = 640f

@Composable
fun SpeedOverlayView(
    trackedVehicles: Map<Int, TrackedVehicle>,
    vehicleSpeeds: Map<Int, Float>,
    detectedBoxes: Map<Int, RectF> = emptyMap(),
    imageWidth: Int,
    imageHeight: Int,
    entryTripwireY: Float,
    exitTripwireY: Float,
    speedUnit: String = "km/h",
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(trackedVehicles.size, imageWidth, imageHeight, entryTripwireY, exitTripwireY) {
        Log.d(
            "OV",
            "SpeedOverlayView recomposed: vehicles=${trackedVehicles.size} imageWidth=$imageWidth imageHeight=$imageHeight entryTripwireY=$entryTripwireY exitTripwireY=$exitTripwireY",
        )
    }
    val lastCanvasLogAtMs = remember { mutableLongStateOf(0L) }
    val textPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            isAntiAlias = true
            setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
        }
    }
    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
         
        val scale = minOf(canvasWidth / imageWidth.toFloat(), canvasHeight / imageHeight.toFloat())
        val drawWidth = imageWidth * scale
        val drawHeight = imageHeight * scale
        val offsetX = (canvasWidth - drawWidth) / 2f
        val offsetY = (canvasHeight - drawHeight) / 2f

        val modelScale = canvasWidth / MODEL_REFERENCE_WIDTH
          
        textPaint.textSize = 28f * (canvasWidth / MODEL_REFERENCE_WIDTH)
        val tagBackgroundHeight = 36f * modelScale
        val tagHorizontalPadding = 8f * modelScale
        val entryTripwireCanvasY = entryTripwireY * drawHeight + offsetY
        val exitTripwireCanvasY = exitTripwireY * drawHeight + offsetY
        val nowMs = System.currentTimeMillis()
        var logged = false
        if (nowMs - lastCanvasLogAtMs.longValue >= 1000L) {
            logged = true
            lastCanvasLogAtMs.longValue = nowMs
            Log.d(
                "OV",
                "Canvas draw: size=${size.width}x${size.height} image=${imageWidth}x${imageHeight} drawSize=${drawWidth}x${drawHeight} offset=($offsetX, $offsetY) entryTripwire=$entryTripwireCanvasY exitTripwire=$exitTripwireCanvasY",
            )
        }
        if (logged) {
            trackedVehicles.entries.firstOrNull()?.let { firstEntry ->
                val firstId = firstEntry.key
                val firstCenter = firstEntry.value.center
                Log.d(
                    "OV",
                    "  first vehicle id=$firstId center=$firstCenter scaledCenter=(${firstCenter.x * drawWidth + offsetX}, ${firstCenter.y * drawHeight + offsetY})",
                )
            }
        }
          drawLine(
            color = Color(0xFFE10600),
            start = Offset(0f, entryTripwireCanvasY),
            end = Offset(size.width, entryTripwireCanvasY),
            strokeWidth = 4f,
        )
       
        drawLine(
            color = Color(0xFFE10600),
            start = Offset(0f, exitTripwireCanvasY),
            end = Offset(size.width, exitTripwireCanvasY),
            strokeWidth = 4f,
        )

          val confirmedVehicles = trackedVehicles.filter { (id, _) ->
            val speed = vehicleSpeeds[id]
            speed != null && speed > 0f
        }

        confirmedVehicles.forEach { (vehicleId, _) ->
            val box = detectedBoxes[vehicleId] ?: return@forEach
        
            val boxW = box.right - box.left
            val boxH = box.bottom - box.top
            val insetX = boxW * 0.12f
            val insetY = boxH * 0.12f
            val tightLeft   = (box.left + insetX)   * drawWidth + offsetX
            val tightTop    = (box.top + insetY)     * drawHeight + offsetY
            val tightRight  = (box.right - insetX)   * drawWidth + offsetX
            val tightBottom = (box.bottom - insetY)  * drawHeight + offsetY

             
            if (tightTop > size.height) return@forEach      
            if (tightRight < 0f) return@forEach           
            if (tightLeft > size.width) return@forEach     

            

              
            val boxColor = when (vehicleId % 4) {
                0 -> Color(0xFFE10600)  
                1 -> Color(0xFF378ADD)  
                2 -> Color(0xFF639922) 
                else -> Color(0xFFEF9F27) 
            }

            drawRect(
                color = boxColor,
                topLeft = Offset(tightLeft, tightTop),
                size = Size(tightRight - tightLeft, tightBottom - tightTop),
                style = Stroke(width = 3f * density),
            )

               
            val speedKmh = vehicleSpeeds[vehicleId]!!
            val displaySpeed = if (speedUnit == "mph") speedKmh * 0.621371f else speedKmh
            val displayNum = trackedVehicles[vehicleId]?.displayId ?: vehicleId
            val label = "CAR #$displayNum  ${displaySpeed.toInt()} $speedUnit"
            val textWidth = textPaint.measureText(label)
            val cx = tightLeft + (tightRight - tightLeft) / 2f
            val tagY = (tightTop - 4f).coerceAtLeast(30f)
            val labelLeft = cx - textWidth / 2f - tagHorizontalPadding
            val labelTop = tagY - tagBackgroundHeight
            val labelRight = labelLeft + textWidth + tagHorizontalPadding * 2f

            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(labelLeft, labelTop),
                size = Size(labelRight - labelLeft, tagBackgroundHeight),
            )
            drawContext.canvas.nativeCanvas.drawText(
                label,
                labelLeft + tagHorizontalPadding,
                tagY - tagHorizontalPadding,
                textPaint,
            )
        }
    }
}
