package com.example.cvproject.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cvproject.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isMph: Boolean = false,
    val speedWarningThreshold: Int = 120,
    val themeSelection: String = "dark",
    val sceneWidthMeters: Float = 3.5f,
    val entryTripwireFraction: Float = 0.5f,
    val exitTripwireFraction: Float = 0.8f,
    val maxConcurrentVehicles: Int = 20,
)

class SettingsViewModel(
    private val preferencesManager: PreferencesManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val baseFlow = combine(
                preferencesManager.isMph,
                preferencesManager.speedWarningThreshold,
                preferencesManager.themeSelection,
                preferencesManager.sceneWidthMeters,
                preferencesManager.entryTripwireFraction
            ) { isMph, speedWarningThreshold, themeSelection, sceneWidthMeters, entryTripwireFraction ->
                SettingsUiState(
                    isMph = isMph,
                    speedWarningThreshold = speedWarningThreshold,
                    themeSelection = themeSelection,
                    sceneWidthMeters = sceneWidthMeters,
                    entryTripwireFraction = entryTripwireFraction,
                    exitTripwireFraction = 0f,
                    maxConcurrentVehicles = 20
                )
            }
            
            val withExitFlow = combine(
                baseFlow,
                preferencesManager.exitTripwireFraction
            ) { state, exitTripwireFraction ->
                state.copy(exitTripwireFraction = exitTripwireFraction)
            }
            
            combine(
                withExitFlow,
                preferencesManager.maxConcurrentVehicles
            ) { state, max ->
                state.copy(maxConcurrentVehicles = max)
            }.collect { state ->
                _uiState.update { state }
            }
        }
    }

    fun setIsMph(isMph: Boolean) {
        viewModelScope.launch {
            preferencesManager.setIsMph(isMph)
        }
    }

    fun setSpeedWarningThreshold(threshold: Int) {
        viewModelScope.launch {
            preferencesManager.setSpeedWarningThreshold(threshold)
        }
    }

    fun setThemeSelection(darkModeEnabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setThemeSelection(if (darkModeEnabled) "dark" else "light")
        }
    }

    fun setSceneWidthMeters(sceneWidthMeters: Float) {
        viewModelScope.launch {
            preferencesManager.setSceneWidthMeters(sceneWidthMeters)
        }
    }

    fun setEntryTripwireFraction(fraction: Float) {
        viewModelScope.launch {
            preferencesManager.setEntryTripwireFraction(fraction)
        }
    }

    fun setExitTripwireFraction(fraction: Float) {
        viewModelScope.launch {
            preferencesManager.setExitTripwireFraction(fraction)
        }
    }

    fun setMaxConcurrentVehicles(max: Int) {
        viewModelScope.launch {
            preferencesManager.setMaxConcurrentVehicles(max)
        }
    }

    companion object {
        fun factory(preferencesManager: PreferencesManager): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return SettingsViewModel(preferencesManager) as T
                    }
                    throw IllegalArgumentException("Expected SettingsViewModel but got ${modelClass.name}")
                }
            }
    }
}
