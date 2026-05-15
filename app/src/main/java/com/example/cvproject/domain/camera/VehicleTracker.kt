package com.example.cvproject.domain.camera

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import kotlin.math.abs

data class TrackedVehicle(
    val id: Int,
    var center: PointF,
    val firstSeenTimeMs: Long,
    var entryTimeMs: Long? = null,
    var entryLine: String? = null,
    var hasCrossedExit: Boolean = false,
    var prevCenterY: Float = -1f,
    var prevSeenMs: Long = -1L,
    val initialY: Float,
    var displayId: Int? = null,
    var lastSeenMs: Long = System.currentTimeMillis(),
)

 
class VehicleTracker {
    var assumedDistanceMeters: Float = 3.5f
    var maxTrackedVehicles: Int = 20

    var nextId = 0
        private set
    private var nextDisplayId = 1
    private val _activeVehicles = mutableMapOf<Int, TrackedVehicle>()
    val activeVehicles: Map<Int, TrackedVehicle> get() = _activeVehicles
    private val _vehicleBoxes = mutableMapOf<Int, RectF>()
    val vehicleBoxes: Map<Int, RectF> = _vehicleBoxes

    val vehicleSpeeds = mutableMapOf<Int, Float>()

     
    private val positionHistory = mutableMapOf<Int, MutableList<Pair<Float, Long>>>()

    var lastCrossingId: Int? = null
        private set

     
    fun processFrame(
        boxes: List<RectF>,
        trackIds: List<Int>,
        entryTripwireY: Float,
        exitTripwireY: Float,
        movementThreshold: Float = 0.005f,
    ): Float? {
        val now = System.currentTimeMillis()
        val topTripwireY = minOf(entryTripwireY, exitTripwireY)
        val bottomTripwireY = maxOf(entryTripwireY, exitTripwireY)

        require(topTripwireY in 0f..1f && bottomTripwireY in 0f..1f) {
            "tripwires must be fractional (0..1)."
        }

        Log.d("VT", "processFrame: boxes=${boxes.size} threshold=$movementThreshold")

        var speedKmh: Float? = null

        boxes.forEachIndexed { index, box ->
            val trackId = trackIds.getOrElse(index) { -1 }
            if (trackId == -1) return@forEachIndexed

            val cx = (box.left + box.right) / 2f
            val cy = (box.top + box.bottom) / 2f
            val center = PointF(cx, cy)

            val existing = _activeVehicles[trackId]
            if (existing != null) {
                existing.prevCenterY = existing.center.y
                existing.prevSeenMs = existing.lastSeenMs
                existing.center = center
                existing.lastSeenMs = now
                _vehicleBoxes[trackId] = box
            } else {
                if (_activeVehicles.size >= maxTrackedVehicles) {
                    return@forEachIndexed
                }
                val vehicle = TrackedVehicle(
                    id = trackId,
                    center = center,
                    firstSeenTimeMs = now,
                    initialY = cy,
                )
                _activeVehicles[trackId] = vehicle
                _vehicleBoxes[trackId] = box
                positionHistory[trackId] = mutableListOf()
                Log.d("VT", "  NEW vehicle trackId=$trackId")
            }

           
            val history = positionHistory.getOrPut(trackId) { mutableListOf() }
            history.add(Pair(cy, now))
            while (history.size > MAX_HISTORY_SIZE) {
                history.removeAt(0)
            }

            
            if (history.size < 2) return@forEachIndexed

            val oldest = history.first()
            val newest = history.last()
            val totalFractionMoved = abs(newest.first - oldest.first)
            val totalTimeMs = newest.second - oldest.second

            
            Log.d("VT-DEBUG", "  trackId=$trackId | cy=${"%.4f".format(cy)} | " +
                "oldestY=${"%.4f".format(oldest.first)} | moved=${"%.5f".format(totalFractionMoved)} | " +
                "threshold=${"%.5f".format(movementThreshold)} | " +
                "PASS=${totalFractionMoved >= movementThreshold} | samples=${history.size}")

           
            if (totalFractionMoved < movementThreshold) {
                vehicleSpeeds.remove(trackId)
                return@forEachIndexed
            }

            if (totalTimeMs > 0) {
                val zoneHeight = abs(bottomTripwireY - topTripwireY)
                if (zoneHeight > 0.01f) {
                    val minY = minOf(oldest.first, newest.first)
                    val maxY = maxOf(oldest.first, newest.first)
                    val overlapStart = maxOf(minY, topTripwireY)
                    val overlapEnd = minOf(maxY, bottomTripwireY)

                    
                    if (overlapStart < overlapEnd || (cy in topTripwireY..bottomTripwireY)) {
                        val physicalDist = (totalFractionMoved / zoneHeight) * assumedDistanceMeters
                        val timeSec = totalTimeMs / 1000f
                        val instSpeedKmh = (physicalDist / timeSec) * KMH_CONVERSION_FACTOR

                        Log.d("VT-DEBUG", "  >> SPEED CALC trackId=$trackId: " +
                            "physDist=${"%.2f".format(physicalDist)}m | time=${"%.2f".format(timeSec)}s | " +
                            "speed=${"%.1f".format(instSpeedKmh)}km/h | zone=[${"%.3f".format(topTripwireY)}..${"%.3f".format(bottomTripwireY)}]")

                          
                        if (instSpeedKmh in 15f..300f) {
                            val currentSpeed = vehicleSpeeds[trackId]
                            vehicleSpeeds[trackId] = if (currentSpeed == null) {
                                instSpeedKmh
                            } else {
                                currentSpeed * 0.6f + instSpeedKmh * 0.4f
                            }

                            val vehicle = _activeVehicles[trackId]!!
                            vehicle.hasCrossedExit = true
                            if (vehicle.displayId == null) {
                                vehicle.displayId = nextDisplayId++
                            }
                            lastCrossingId = trackId
                            val smoothed = vehicleSpeeds[trackId]!!
                            if (speedKmh == null || smoothed > speedKmh!!) {
                                speedKmh = smoothed
                            }
                            Log.d("VT", "  SPEED trackId=$trackId inst=$instSpeedKmh smoothed=$smoothed")
                        }
                    }
                }
            }
        }

         
        _activeVehicles.entries.removeIf { (id, vehicle) ->
            val notSeenFor = now - vehicle.lastSeenMs
            val shouldEvict = notSeenFor > STALE_VEHICLE_TIMEOUT_MS
            if (shouldEvict) {
                _vehicleBoxes.remove(id)
                vehicleSpeeds.remove(id)
                positionHistory.remove(id)
                Log.d("VT", "  EVICT trackId=$id stale(${notSeenFor}ms)")
            }
            shouldEvict
        }

        return speedKmh
    }

    @Synchronized
    fun clearSession() {
        _activeVehicles.clear()
        _vehicleBoxes.clear()
        vehicleSpeeds.clear()
        positionHistory.clear()
        lastCrossingId = null
        nextId = 0
        nextDisplayId = 1
    }

    private companion object {
        private const val STALE_VEHICLE_TIMEOUT_MS = 1500L
        private const val KMH_CONVERSION_FACTOR = 3.6f
        private const val MIN_MOVEMENT_FRACTION = 0.005f
        private const val MAX_HISTORY_SIZE = 6
    }
}
