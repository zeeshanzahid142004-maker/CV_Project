package com.example.cvproject.domain.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

 
class ShakeDetector(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

     
    private val accelHistory = mutableListOf<Pair<Float, Long>>()  

    
    val dynamicMovementThreshold: Float
        get() {
            val now = System.currentTimeMillis()
            
            accelHistory.removeAll { now - it.second > PEAK_HOLD_WINDOW_MS }
        
            val peakAccel = accelHistory.maxOfOrNull { it.first } ?: 0f
            return BASE_THRESHOLD + (peakAccel * SHAKE_TO_THRESHOLD_SCALE)
        }

    val currentAcceleration: Float
        get() {
            val now = System.currentTimeMillis()
            accelHistory.removeAll { now - it.second > PEAK_HOLD_WINDOW_MS }
            return accelHistory.maxOfOrNull { it.first } ?: 0f
        }

    fun start() {
        linearAccelSensor?.let {
             
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            Log.d(TAG, "ShakeDetector started (GAME rate)")
        } ?: Log.w(TAG, "Linear acceleration sensor not available!")
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        accelHistory.clear()
        Log.d(TAG, "ShakeDetector stopped")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_LINEAR_ACCELERATION) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)

        accelHistory.add(Pair(magnitude, System.currentTimeMillis()))

      
        if (accelHistory.size > MAX_SAMPLES) {
            accelHistory.removeAt(0)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val TAG = "ShakeDetector"
        
        private const val BASE_THRESHOLD = 0.02f
        
        private const val SHAKE_TO_THRESHOLD_SCALE = 0.02f
         
        private const val PEAK_HOLD_WINDOW_MS = 2000L
        
        private const val MAX_SAMPLES = 150
    }
}
