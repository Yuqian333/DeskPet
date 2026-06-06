package com.example.deskcat.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.petSettingsDataStore by preferencesDataStore(name = "pet_settings")

class PetPreferencesRepository(private val context: Context) {
    private object Keys {
        val imageUri = stringPreferencesKey("image_uri")
        val sizeScale = floatPreferencesKey("size_scale")
        val sizePreset = stringPreferencesKey("size_preset")
        val autoMoveEnabled = booleanPreferencesKey("auto_move_enabled")
        val petStyle = stringPreferencesKey("pet_style")
        val detectedLabel = stringPreferencesKey("detected_label")
        val petPackDir = stringPreferencesKey("pet_pack_dir")
    }

    val settingsFlow: Flow<PetSettingsUiState> = context.petSettingsDataStore.data.map { preferences ->
        preferences.toUiState()
    }

    suspend fun setImageUri(uri: String?) {
        context.petSettingsDataStore.edit { preferences ->
            if (uri.isNullOrBlank()) {
                preferences.remove(Keys.imageUri)
            } else {
                preferences[Keys.imageUri] = uri
            }
        }
    }

    suspend fun setSizeScale(scale: Float) {
        context.petSettingsDataStore.edit { preferences ->
            preferences[Keys.sizeScale] = scale.coerceIn(PET_SIZE_SCALE_MIN, PET_SIZE_SCALE_MAX)
        }
    }

    suspend fun setSizePreset(preset: PetSizePreset) {
        context.petSettingsDataStore.edit { preferences ->
            preferences[Keys.sizePreset] = preset.name
        }
    }

    suspend fun setAutoMoveEnabled(enabled: Boolean) {
        context.petSettingsDataStore.edit { preferences ->
            preferences[Keys.autoMoveEnabled] = enabled
        }
    }

    suspend fun setPetStyle(style: PetStyle, detectedLabel: String?) {
        context.petSettingsDataStore.edit { preferences ->
            preferences[Keys.petStyle] = style.name
            if (detectedLabel.isNullOrBlank()) {
                preferences.remove(Keys.detectedLabel)
            } else {
                preferences[Keys.detectedLabel] = detectedLabel
            }
        }
    }

    suspend fun setPetPackDir(dir: String?) {
        context.petSettingsDataStore.edit { preferences ->
            if (dir.isNullOrBlank()) {
                preferences.remove(Keys.petPackDir)
            } else {
                preferences[Keys.petPackDir] = dir
            }
        }
    }

    private fun Preferences.toUiState(): PetSettingsUiState {
        val preset = this[Keys.sizePreset]
            ?.let { runCatching { PetSizePreset.valueOf(it) }.getOrNull() }
            ?: PetSizePreset.Medium

        val style = this[Keys.petStyle]
            ?.let { runCatching { PetStyle.valueOf(it) }.getOrNull() }
            ?: PetStyle.Default

        return PetSettingsUiState(
            imageUri = this[Keys.imageUri],
            sizeScale = (this[Keys.sizeScale] ?: 1f).coerceIn(PET_SIZE_SCALE_MIN, PET_SIZE_SCALE_MAX),
            sizePreset = preset,
            autoMoveEnabled = this[Keys.autoMoveEnabled] ?: true,
            petStyle = style,
            detectedLabel = this[Keys.detectedLabel],
            petPackDir = this[Keys.petPackDir],
        )
    }
}
