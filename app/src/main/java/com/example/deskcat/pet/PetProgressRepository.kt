package com.example.deskcat.pet

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.deskcat.DesktopPetUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.petProgressDataStore by preferencesDataStore(name = "pet_progress")

data class PetProgressSnapshot(
    val hunger: Int = 35,
    val happiness: Int = 70,
    val energy: Int = 80,
    val coins: Int = 1000,
    val petCount: Int = 0,
    val lastUpdatedAtMillis: Long = System.currentTimeMillis(),
)

class PetProgressRepository(private val context: Context) {
    private object Keys {
        val hunger = intPreferencesKey("hunger")
        val happiness = intPreferencesKey("happiness")
        val energy = intPreferencesKey("energy")
        val coins = intPreferencesKey("coins")
        val petCount = intPreferencesKey("pet_count")
        val lastUpdatedAtMillis = longPreferencesKey("last_updated_at_millis")
    }

    val progressFlow: Flow<PetProgressSnapshot> = context.petProgressDataStore.data.map { preferences ->
        preferences.toSnapshot()
    }

    suspend fun save(state: DesktopPetUiState) {
        context.petProgressDataStore.edit { preferences ->
            preferences[Keys.hunger] = state.hunger
            preferences[Keys.happiness] = state.happiness
            preferences[Keys.energy] = state.energy
            preferences[Keys.coins] = state.coins.coerceIn(0, MAX_COINS)
            preferences[Keys.petCount] = state.petCount
            preferences[Keys.lastUpdatedAtMillis] = System.currentTimeMillis()
        }
    }

    private fun Preferences.toSnapshot(): PetProgressSnapshot {
        return PetProgressSnapshot(
            hunger = this[Keys.hunger] ?: 35,
            happiness = this[Keys.happiness] ?: 70,
            energy = this[Keys.energy] ?: 80,
            coins = (this[Keys.coins] ?: 1000).coerceIn(0, MAX_COINS),
            petCount = this[Keys.petCount] ?: 0,
            lastUpdatedAtMillis = this[Keys.lastUpdatedAtMillis] ?: System.currentTimeMillis(),
        )
    }

    companion object {
        const val MAX_COINS = 99_999
    }
}
