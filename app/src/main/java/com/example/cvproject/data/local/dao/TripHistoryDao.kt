package com.example.cvproject.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cvproject.data.local.entity.TripHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface TripHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripHistory)

    @Query("SELECT * FROM trip_history ORDER BY timestamp DESC")
    fun getAllTrips(): Flow<List<TripHistory>>

    @Query("SELECT * FROM trip_history WHERE id = :id")
    fun getTripById(id: Long): Flow<TripHistory?>

    @Delete
    suspend fun deleteTrip(trip: TripHistory)

    @Query("DELETE FROM trip_history")
    suspend fun deleteAllTrips()
}
