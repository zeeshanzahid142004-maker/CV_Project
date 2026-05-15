package com.example.cvproject.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cvproject.data.local.entity.TripSession
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: TripSession)

    @Query("SELECT * FROM trip_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<TripSession>>
}
