package com.example.cvproject.data.repository

import com.example.cvproject.data.local.dao.TripDao
import com.example.cvproject.data.local.entity.TripSession
import kotlinx.coroutines.flow.Flow

class TripRepositoryImpl(
    private val tripDao: TripDao,
) : TripRepository {
    override fun getAllSessions(): Flow<List<TripSession>> = tripDao.getAllSessions()

    override suspend fun insertSession(session: TripSession) = tripDao.insertSession(session)
}
