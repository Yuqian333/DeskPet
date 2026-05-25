package com.example.deskcat.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PetSettingsViewModel(
    private val repository: PetPreferencesRepository,
) : ViewModel() {
    val uiState: StateFlow<PetSettingsUiState> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PetSettingsUiState(),
    )

    fun setImageUri(uri: String?) {
        viewModelScope.launch {
            repository.setImageUri(uri)
        }
    }

    fun setSizeScale(scale: Float) {
        viewModelScope.launch {
            repository.setSizeScale(scale)
        }
    }

    fun setSizePreset(preset: PetSizePreset) {
        viewModelScope.launch {
            repository.setSizePreset(preset)
        }
    }

    fun setAutoMoveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoMoveEnabled(enabled)
        }
    }

    class Factory(
        private val repository: PetPreferencesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PetSettingsViewModel::class.java)) {
                return PetSettingsViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
