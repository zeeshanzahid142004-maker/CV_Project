package com.example.cvproject

import android.app.Application
import androidx.room.Room
import com.example.cvproject.data.local.database.AppDatabase
import com.example.cvproject.data.preferences.PreferencesManager
import com.example.cvproject.data.repository.TripRepository
import com.example.cvproject.data.repository.TripRepositoryImpl

class CvProjectApp : Application() {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "cv_project.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    val preferencesManager: PreferencesManager by lazy {
        PreferencesManager(this)
    }

    val tripRepository: TripRepository by lazy {
        TripRepositoryImpl(database.tripDao())
    }
}
