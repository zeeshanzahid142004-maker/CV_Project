package com.example.cvproject.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trip_sessions")
data class TripSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val maxSpeed: Float,
    val avgSpeed: Float,
    val distance: Float,
    val timestamp: Long = System.currentTimeMillis(),
)
