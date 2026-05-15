package com.example.cvproject.data.repository

import com.example.cvproject.data.local.entity.TripHistory
import kotlinx.coroutines.flow.Flow

interface SpeedRepository {
    fun getAllTrips(): Flow<List<TripHistory>>
    fun getTripById(id: Long): Flow<TripHistory?>
    suspend fun saveTrip(trip: TripHistory)
    suspend fun deleteTrip(trip: TripHistory)
    suspend fun deleteAllTrips()

    fun getUseMph(): Flow<Boolean>
    fun getSpeedLimitThreshold(): Flow<Float>
    fun getThemeSelection(): Flow<String>

    suspend fun setUseMph(useMph: Boolean)
    suspend fun setSpeedLimitThreshold(threshold: Float)
    suspend fun setThemeSelection(theme: String)
}
