package com.example.cvproject.domain.speed

const val START_ANGLE = 140f
private const val TOTAL_SWEEP = 260f
private const val MAX_SPEED = 300f

fun getAngle(speed: Float): Float {
    val clampedSpeed = speed.coerceIn(0f, MAX_SPEED)
    return START_ANGLE + (clampedSpeed / MAX_SPEED) * TOTAL_SWEEP
}
