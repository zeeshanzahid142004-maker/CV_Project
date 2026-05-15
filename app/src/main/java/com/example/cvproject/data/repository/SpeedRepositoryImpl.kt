package com.example.cvproject.data.repository

import com.example.cvproject.data.local.dao.TripHistoryDao
import com.example.cvproject.data.local.entity.TripHistory
import com.example.cvproject.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.Flow

class SpeedRepositoryImpl(
    private val tripHistoryDao: TripHistoryDao,
    private val preferencesManager: PreferencesManager,
) : SpeedRepository {

    override fun getAllTrips(): Flow<List<TripHistory>> =
        tripHistoryDao.getAllTrips()

    override fun getTripById(id: Long): Flow<TripHistory?> =
        tripHistoryDao.getTripById(id)

    override suspend fun saveTrip(trip: TripHistory) =
        tripHistoryDao.insertTrip(trip)

    override suspend fun deleteTrip(trip: TripHistory) =
        tripHistoryDao.deleteTrip(trip)

    override suspend fun deleteAllTrips() =
        tripHistoryDao.deleteAllTrips()

    override fun getUseMph(): Flow<Boolean> =
        preferencesManager.useMph

    override fun getSpeedLimitThreshold(): Flow<Float> =
        preferencesManager.speedLimitThreshold

    override fun getThemeSelection(): Flow<String> =
        preferencesManager.themeSelection

    override suspend fun setUseMph(useMph: Boolean) =
        preferencesManager.setUseMph(useMph)

    override suspend fun setSpeedLimitThreshold(threshold: Float) =
        preferencesManager.setSpeedLimitThreshold(threshold)

    override suspend fun setThemeSelection(theme: String) =
        preferencesManager.setThemeSelection(theme)
}
