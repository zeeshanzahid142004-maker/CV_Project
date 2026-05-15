package com.example.cvproject.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "speed_tracker_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_IS_MPH = booleanPreferencesKey("is_mph")
        private val KEY_SPEED_WARNING_THRESHOLD = intPreferencesKey("speed_warning_threshold")
        private val KEY_THEME_SELECTION = stringPreferencesKey("theme_selection")
        private val KEY_SCENE_WIDTH_METERS = floatPreferencesKey("scene_width_meters")
        private val KEY_ENTRY_TRIPWIRE_FRACTION = floatPreferencesKey("entry_tripwire_fraction")
        private val KEY_EXIT_TRIPWIRE_FRACTION = floatPreferencesKey("exit_tripwire_fraction")
        private val KEY_MAX_CONCURRENT_VEHICLES = intPreferencesKey("max_concurrent_vehicles")
        private val KEY_ALL_TIME_MAX_SPEED = floatPreferencesKey("all_time_max_speed")
        private val KEY_TOTAL_SPEED_SUM = floatPreferencesKey("total_speed_sum")
        private val KEY_TOTAL_SPEED_COUNT = intPreferencesKey("total_speed_count")
        private val LEGACY_KEY_USE_MPH = booleanPreferencesKey("use_mph")
        private val LEGACY_KEY_SPEED_LIMIT_THRESHOLD = floatPreferencesKey("speed_limit_threshold")
    }

    val isMph: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_IS_MPH] ?: prefs[LEGACY_KEY_USE_MPH] ?: false }

    val speedWarningThreshold: Flow<Int> = context.dataStore.data
        .map { prefs ->
            prefs[KEY_SPEED_WARNING_THRESHOLD]
                ?: prefs[LEGACY_KEY_SPEED_LIMIT_THRESHOLD]?.toInt()
                ?: 120
        }

    @Deprecated("Use isMph instead")
    val useMph: Flow<Boolean> = isMph

    @Deprecated("Use speedWarningThreshold instead")
    val speedLimitThreshold: Flow<Float> = speedWarningThreshold.map { it.toFloat() }

    val themeSelection: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_THEME_SELECTION] ?: "dark" }

    val sceneWidthMeters: Flow<Float> = context.dataStore.data
        .map { prefs -> prefs[KEY_SCENE_WIDTH_METERS] ?: 3.5f }

    val entryTripwireFraction: Flow<Float> = context.dataStore.data
        .map { prefs -> prefs[KEY_ENTRY_TRIPWIRE_FRACTION] ?: 0.5f }

    val exitTripwireFraction: Flow<Float> = context.dataStore.data
        .map { prefs -> prefs[KEY_EXIT_TRIPWIRE_FRACTION] ?: 0.8f }

    val maxConcurrentVehicles: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[KEY_MAX_CONCURRENT_VEHICLES] ?: 20 }

    val allTimeMaxSpeed: Flow<Float> = context.dataStore.data
        .map { prefs -> prefs[KEY_ALL_TIME_MAX_SPEED] ?: 0f }

    val totalSpeedSum: Flow<Float> = context.dataStore.data
        .map { prefs -> prefs[KEY_TOTAL_SPEED_SUM] ?: 0f }

    val totalSpeedCount: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[KEY_TOTAL_SPEED_COUNT] ?: 0 }

    suspend fun setIsMph(isMph: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_IS_MPH] = isMph }
    }

    suspend fun setSpeedWarningThreshold(threshold: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_SPEED_WARNING_THRESHOLD] = threshold }
    }

    @Deprecated("Use setIsMph instead")
    suspend fun setUseMph(useMph: Boolean) {
        setIsMph(useMph)
    }

    @Deprecated("Use setSpeedWarningThreshold instead")
    suspend fun setSpeedLimitThreshold(threshold: Float) {
        setSpeedWarningThreshold(threshold.toInt())
    }

    suspend fun setThemeSelection(theme: String) {
        context.dataStore.edit { prefs -> prefs[KEY_THEME_SELECTION] = theme }
    }

    suspend fun setSceneWidthMeters(sceneWidthMeters: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_SCENE_WIDTH_METERS] = sceneWidthMeters }
    }

    suspend fun setEntryTripwireFraction(fraction: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_ENTRY_TRIPWIRE_FRACTION] = fraction }
    }

    suspend fun setExitTripwireFraction(fraction: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_EXIT_TRIPWIRE_FRACTION] = fraction }
    }

    suspend fun setMaxConcurrentVehicles(max: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_MAX_CONCURRENT_VEHICLES] = max }
    }

    suspend fun recordSpeed(speedKmh: Float) {
        context.dataStore.edit { prefs ->
            val currentMax = prefs[KEY_ALL_TIME_MAX_SPEED] ?: 0f
            if (speedKmh > currentMax) {
                prefs[KEY_ALL_TIME_MAX_SPEED] = speedKmh
            }
            prefs[KEY_TOTAL_SPEED_SUM] = (prefs[KEY_TOTAL_SPEED_SUM] ?: 0f) + speedKmh
            prefs[KEY_TOTAL_SPEED_COUNT] = (prefs[KEY_TOTAL_SPEED_COUNT] ?: 0) + 1
        }
    }
}
