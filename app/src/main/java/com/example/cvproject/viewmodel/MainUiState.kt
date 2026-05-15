package com.example.cvproject.viewmodel

import androidx.compose.ui.geometry.Rect

data class TrackedCar(
    val id: Int,
    val boundingBox: Rect,
    val speedKph: Float,
    val previousSpeedKph: Float = speedKph,
) {
    val speedTrend: SpeedTrend
        get() = when {
            speedKph > previousSpeedKph + 1f -> SpeedTrend.ACCELERATING
            speedKph < previousSpeedKph - 1f -> SpeedTrend.DECELERATING
            else -> SpeedTrend.CONSTANT
        }
}

enum class SpeedTrend { ACCELERATING, CONSTANT, DECELERATING }

data class MainUiState(
    val currentSpeed: Float = 0f,
    val maxSpeed: Float = 0f,
    val avgSpeed: Float = 0f,
    val distance: Float = 0f,
    val allTimeMaxSpeed: Float = 0f,
    val allTimeAvgSpeed: Float = 0f,
    val totalCarsTracked: Int = 0,
    val isTracking: Boolean = false,
    val speedUnit: String = "km/h",
    val speedLimitThreshold: Float = 120f,
    val entryTripwireFraction: Float = 0.5f,
    val exitTripwireFraction: Float = 0.8f,
    val maxConcurrentVehicles: Int = 20,
    val trackedCars: List<TrackedCar> = emptyList(),
)
