package com.example.cvproject.domain.speed

 
class SpeedCalculator(
    var tripwireTopY: Float,
    var tripwireBottomY: Float,
    var realWorldDistanceMeters: Float,
    private val currentTimeMillis: () -> Long = { System.currentTimeMillis() },
) {
     
    private val topCrossingTimestamps = HashMap<Int, Long>()

    
    private val computedSpeeds = HashMap<Int, Float>()

    
    
      
    fun onVehiclePosition(vehicleId: Int, centreY: Float): Float? {
        val now = currentTimeMillis()

        
        if (centreY >= tripwireTopY && vehicleId !in topCrossingTimestamps) {
            topCrossingTimestamps[vehicleId] = now
            return null
        }

        // Vehicle crosses the BOTTOM wire after already passing the top wire
        if (centreY >= tripwireBottomY && vehicleId in topCrossingTimestamps) {
            val elapsedMs = now - topCrossingTimestamps.getValue(vehicleId)
            topCrossingTimestamps.remove(vehicleId)

            if (elapsedMs <= 0) return null

            val speedMs = realWorldDistanceMeters / (elapsedMs / 1000.0)
            val speedKmh = (speedMs * 3.6).toFloat()
            computedSpeeds[vehicleId] = speedKmh
            return speedKmh
        }

        return null
    }

    
    fun getLastSpeed(vehicleId: Int): Float? = computedSpeeds[vehicleId]

    
    
     
    fun onVehicleRemoved(vehicleId: Int) {
        topCrossingTimestamps.remove(vehicleId)
        computedSpeeds.remove(vehicleId)
    }

     
    fun reset() {
        topCrossingTimestamps.clear()
        computedSpeeds.clear()
    }
}
