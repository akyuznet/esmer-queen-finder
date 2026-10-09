package com.esmer.queenfinder.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val confidence: Float = 0.4f,
    val stableFrames: Int = 5,
    val haptics: Boolean = true,
    val sound: Boolean = true,
    val showDrones: Boolean = true,
    val showStats: Boolean = true,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val confidence = floatPreferencesKey("confidence")
        val stableFrames = intPreferencesKey("stable_frames")
        val haptics = booleanPreferencesKey("haptics")
        val sound = booleanPreferencesKey("sound")
        val showDrones = booleanPreferencesKey("show_drones")
        val showStats = booleanPreferencesKey("show_stats")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            confidence = p[Keys.confidence] ?: 0.4f,
            stableFrames = p[Keys.stableFrames] ?: 5,
            haptics = p[Keys.haptics] ?: true,
            sound = p[Keys.sound] ?: true,
            showDrones = p[Keys.showDrones] ?: true,
            showStats = p[Keys.showStats] ?: true,
        )
    }

    suspend fun setConfidence(v: Float) = context.dataStore.edit { it[Keys.confidence] = v.coerceIn(0.1f, 0.95f) }
    suspend fun setStableFrames(v: Int) = context.dataStore.edit { it[Keys.stableFrames] = v.coerceIn(1, 15) }
    suspend fun setHaptics(v: Boolean) = context.dataStore.edit { it[Keys.haptics] = v }
    suspend fun setSound(v: Boolean) = context.dataStore.edit { it[Keys.sound] = v }
    suspend fun setShowDrones(v: Boolean) = context.dataStore.edit { it[Keys.showDrones] = v }
    suspend fun setShowStats(v: Boolean) = context.dataStore.edit { it[Keys.showStats] = v }
}
