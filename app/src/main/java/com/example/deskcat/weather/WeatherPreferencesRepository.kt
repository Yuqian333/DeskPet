package com.example.deskcat.weather

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.weatherSettingsDataStore by preferencesDataStore(name = "weather_settings")

data class WeatherSettings(
    val city: String = DEFAULT_CITY,
)

class WeatherPreferencesRepository(private val context: Context) {
    private object Keys {
        val city = stringPreferencesKey("city")
    }

    val settingsFlow: Flow<WeatherSettings> = context.weatherSettingsDataStore.data.map { preferences ->
        preferences.toSettings()
    }

    suspend fun setCity(city: String) {
        val normalizedCity = city.trim().ifBlank { DEFAULT_CITY }
        context.weatherSettingsDataStore.edit { preferences ->
            preferences[Keys.city] = normalizedCity
        }
    }

    private fun Preferences.toSettings(): WeatherSettings {
        return WeatherSettings(
            city = this[Keys.city]?.trim()?.ifBlank { DEFAULT_CITY } ?: DEFAULT_CITY,
        )
    }
}
