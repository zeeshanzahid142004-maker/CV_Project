package com.example.cvproject.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cvproject.data.local.entity.TripSession
import com.example.cvproject.data.repository.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TripLogUiState(
    val sessions: List<TripSession> = emptyList(),
)

class TripViewModel(
    private val repository: TripRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TripLogUiState())
    val uiState: StateFlow<TripLogUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllSessions().collect { sessions ->
                _uiState.update { it.copy(sessions = sessions) }
            }
        }
    }

    companion object {
        fun factory(repository: TripRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(TripViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return TripViewModel(repository) as T
                    }
                    throw IllegalArgumentException("Expected TripViewModel but got ${modelClass.name}")
                }
            }
    }
}
