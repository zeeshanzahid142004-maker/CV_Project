package com.example.cvproject.domain.tracking

import android.graphics.RectF

 
class VehicleTracker(private val maxAgeMs: Long = 1500L) {

    data class TrackedVehicle(
        val id: Int,
        var rect: RectF,
        var lastSeenTimestamp: Long,
    )

    private val active = mutableListOf<TrackedVehicle>()
    private var nextId = 1

     
    fun update(detections: List<RectF>): List<TrackedVehicle> {
        val now = System.currentTimeMillis()

         
        val matched = mutableSetOf<Int>() 
        for (track in active) {
            var bestIou = 0f
            var bestIdx = -1
            detections.forEachIndexed { idx, det ->
                if (idx in matched) return@forEachIndexed
                val iouVal = iou(track.rect, det)
                if (iouVal > bestIou) {
                    bestIou = iouVal
                    bestIdx = idx
                }
            }
            if (bestIou > 0.3f && bestIdx >= 0) {
                track.rect = detections[bestIdx]
                track.lastSeenTimestamp = now
                matched.add(bestIdx)
            }
        }

         
        detections.forEachIndexed { idx, det ->
            if (idx !in matched) {
                active.add(TrackedVehicle(id = nextId++, rect = det, lastSeenTimestamp = now))
            }
        }

         
        active.removeAll { (now - it.lastSeenTimestamp) > maxAgeMs }

        return active.toList()
    }

   

    private fun iou(a: RectF, b: RectF): Float {
        val interLeft   = maxOf(a.left, b.left)
        val interTop    = maxOf(a.top, b.top)
        val interRight  = minOf(a.right, b.right)
        val interBottom = minOf(a.bottom, b.bottom)
        val interArea = maxOf(0f, interRight - interLeft) * maxOf(0f, interBottom - interTop)
        if (interArea == 0f) return 0f
        val unionArea = a.width() * a.height() + b.width() * b.height() - interArea
        return if (unionArea <= 0f) 0f else interArea / unionArea
    }
}
