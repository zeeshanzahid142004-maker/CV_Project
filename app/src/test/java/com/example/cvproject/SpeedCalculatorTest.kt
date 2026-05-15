package com.example.cvproject

import com.example.cvproject.domain.speed.START_ANGLE
import com.example.cvproject.domain.speed.SpeedCalculator
import com.example.cvproject.domain.speed.getAngle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedCalculatorTest {

    @Test
    fun calculateSpeed_isCorrect() {
        var now = 1_000L
        val calculator = SpeedCalculator(
            tripwireTopY = 100f,
            tripwireBottomY = 150f,
            realWorldDistanceMeters = 50f,
            currentTimeMillis = { now },
        )

        assertNull(calculator.onVehiclePosition(vehicleId = 1, centreY = 99f))
        assertNull(calculator.onVehiclePosition(vehicleId = 1, centreY = 100f))

        now += 2_500L
        val speed = calculator.onVehiclePosition(vehicleId = 1, centreY = 150f)

        assertNotNull(speed)
        assertEquals(72f, speed!!, 0.001f)
    }

    @Test
    fun getAngle_isCorrect() {
        assertEquals(START_ANGLE, getAngle(0f), 0.001f)
        assertEquals(400f, getAngle(300f), 0.001f)
        assertEquals(270f, getAngle(150f), 0.001f)
        assertEquals(400f, getAngle(500f), 0.001f)
    }
}
