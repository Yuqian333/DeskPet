package com.example.deskcat.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WeatherViewModel(
    private val repository: WeatherRepository,
) : ViewModel() {
    val uiState: StateFlow<WeatherUiState> = repository.uiState

    fun updateCityInput(city: String) {
        repository.updateCityInput(city)
    }

    fun saveCityInput() {
        viewModelScope.launch {
            repository.saveCityInput()
        }
    }

    fun refreshManualWeather(speak: Boolean = true) {
        viewModelScope.launch {
            repository.refreshManualCity(speak = speak)
        }
    }

    fun refreshDeviceWeather(latitude: Double, longitude: Double, speak: Boolean = true) {
        viewModelScope.launch {
            repository.refreshDeviceLocation(
                latitude = latitude,
                longitude = longitude,
                speak = speak,
            )
        }
    }

    fun refreshSavedOrCachedWeather(speak: Boolean = true) {
        viewModelScope.launch {
            repository.cachedOrRefreshSavedCity(speak = speak)
        }
    }

    class Factory(
        private val repository: WeatherRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
                return WeatherViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
