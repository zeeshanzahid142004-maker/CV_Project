package com.example.cvproject.data.repository

import com.example.cvproject.data.local.entity.TripSession
import kotlinx.coroutines.flow.Flow

interface TripRepository {
    fun getAllSessions(): Flow<List<TripSession>>
    suspend fun insertSession(session: TripSession)
}
