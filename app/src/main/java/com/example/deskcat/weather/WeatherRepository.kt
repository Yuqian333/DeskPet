package com.example.deskcat.weather

import android.content.Context
import com.example.deskcat.pet.PetStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WeatherRepository(
    private val preferencesRepository: WeatherPreferencesRepository,
    private val client: QWeatherClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _uiState = MutableStateFlow(WeatherUiState(serviceConfigured = client.isConfigured))
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()
    private var cachedReport: WeatherReport? = null
    private var cachedAtMillis: Long = 0L

    init {
        scope.launch {
            preferencesRepository.settingsFlow.collect { settings ->
                _uiState.update { it.copy(cityInput = settings.city) }
            }
        }
    }

    fun updateCityInput(city: String) {
        _uiState.update { it.copy(cityInput = city) }
    }

    suspend fun saveCityInput() {
        preferencesRepository.setCity(_uiState.value.cityInput)
    }

    suspend fun refreshManualCity(speak: Boolean = false): Result<WeatherReport> {
        val city = _uiState.value.cityInput.trim().ifBlank { DEFAULT_CITY }
        preferencesRepository.setCity(city)
        return refreshCity(city, speak)
    }

    suspend fun refreshDeviceLocation(latitude: Double, longitude: Double, speak: Boolean = false): Result<WeatherReport> {
        val location = "${longitude},${latitude}"
        _uiState.update {
            it.copy(loading = true, usingDeviceLocation = true, errorMessage = null)
        }
        val result = client.getWeatherNow(location = location, cityName = "当前位置")
        applyResult(result, speak)
        return result
    }

    suspend fun cachedOrRefreshSavedCity(speak: Boolean = false): Result<WeatherReport> {
        val cached = cachedReport
        val freshEnough = cached != null && System.currentTimeMillis() - cachedAtMillis < CACHE_TTL_MILLIS
        if (freshEnough) {
            if (speak) {
                PetStateRepository.setSpeech(cached!!.toPetSpeech())
            }
            return Result.success(cached!!)
        }
        val city = preferencesRepository.settingsFlow.first().city
        return refreshCity(city, speak)
    }

    private suspend fun refreshCity(city: String, speak: Boolean): Result<WeatherReport> {
        _uiState.update {
            it.copy(loading = true, usingDeviceLocation = false, errorMessage = null, cityInput = city)
        }
        val location = WeatherCityResolver.resolve(city)
        val weatherResult = if (location != null) {
            client.getWeatherNow(location = location.id, cityName = location.name)
        } else {
            Result.failure(
                IllegalStateException("暂时无法识别“$city”。${WeatherCityResolver.supportedCityHint()}"),
            )
        }
        applyResult(weatherResult, speak)
        return weatherResult
    }

    private fun applyResult(result: Result<WeatherReport>, speak: Boolean) {
        result.fold(
            onSuccess = { report ->
                cachedReport = report
                cachedAtMillis = System.currentTimeMillis()
                _uiState.update {
                    it.copy(
                        loading = false,
                        report = report,
                        errorMessage = null,
                    )
                }
                if (speak) {
                    PetStateRepository.setSpeech(report.toPetSpeech())
                }
            },
            onFailure = { error ->
                val message = error.message ?: "天气获取失败，请稍后再试。"
                _uiState.update {
                    it.copy(loading = false, errorMessage = message)
                }
                if (speak) {
                    PetStateRepository.setSpeech(message)
                }
            },
        )
    }

    private companion object {
        const val CACHE_TTL_MILLIS = 10 * 60 * 1000L
    }
}

object WeatherRepositoryProvider {
    @Volatile
    private var instance: WeatherRepository? = null

    fun get(context: Context): WeatherRepository {
        return instance ?: synchronized(this) {
            instance ?: WeatherRepository(
                preferencesRepository = WeatherPreferencesRepository(context.applicationContext),
                client = QWeatherClient(context.applicationContext),
            ).also { instance = it }
        }
    }
}
