package com.skysplatwoomy.petblud

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.petSettingsDataStore by preferencesDataStore(name = "pet_settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.petSettingsDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            val seedy = PetSettings(
                gravity = preferences[GRAVITY] ?: true,
                tiltGravity = preferences[TILT_GRAVITY] ?: false,
                shakeDetonation = preferences[SHAKE_DETONATION] ?: false,
                keepUpright = preferences[KEEP_UPRIGHT] ?: false,
                avoidKeyboard = preferences[AVOID_KEYBOARD] ?: false,
                physicsMultiplier = preferences[PHYSICS_MULTIPLIER] ?: 1.0,
                sizePercent = preferences[SIZE_PERCENT] ?: 100.0,
                collision = preferences[COLLISION] ?: false,
            ).normalized()
            val hoots = PetSettings(
                gravity = preferences[HOOTS_GRAVITY] ?: true,
                tiltGravity = preferences[HOOTS_TILT_GRAVITY] ?: false,
                shakeDetonation = preferences[HOOTS_SHAKE_DETONATION] ?: false,
                keepUpright = preferences[HOOTS_KEEP_UPRIGHT] ?: false,
                avoidKeyboard = preferences[HOOTS_AVOID_KEYBOARD] ?: false,
                physicsMultiplier = preferences[HOOTS_PHYSICS_MULTIPLIER] ?: 1.0,
                sizePercent = preferences[HOOTS_SIZE_PERCENT] ?: 100.0,
                collision = false,
            ).normalized()
            AppSettings(
                seedy = seedy,
                hoots = hoots,
                seedyHootsCollision = preferences[SEEDY_HOOTS_COLLISION] ?: false,
            )
        }

    suspend fun read(): AppSettings = settings.first()

    suspend fun save(value: AppSettings) {
        val normalized = value.normalized()
        dataStore.edit { preferences ->
            writeSeedy(preferences, normalized.seedy)
            writeHoots(preferences, normalized.hoots)
            preferences[SEEDY_HOOTS_COLLISION] = normalized.seedyHootsCollision
        }
    }

    suspend fun saveDashboard(
        species: PetSpecies,
        value: PetSettings,
        seedyHootsCollision: Boolean? = null,
    ) {
        val normalized = value.normalized()
        dataStore.edit { preferences ->
            when (species) {
                PetSpecies.SEEDY -> writeSeedy(preferences, normalized)
                PetSpecies.HOOTS -> writeHoots(preferences, normalized)
            }
            if (seedyHootsCollision != null) {
                preferences[SEEDY_HOOTS_COLLISION] = seedyHootsCollision
            }
        }
    }

    private fun writeSeedy(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        value: PetSettings,
    ) {
        preferences[GRAVITY] = value.gravity
        preferences[TILT_GRAVITY] = value.tiltGravity
        preferences[SHAKE_DETONATION] = value.shakeDetonation
        preferences[KEEP_UPRIGHT] = value.keepUpright
        preferences[AVOID_KEYBOARD] = value.avoidKeyboard
        preferences[PHYSICS_MULTIPLIER] = value.physicsMultiplier
        preferences[SIZE_PERCENT] = value.sizePercent
        preferences[COLLISION] = value.collision
    }

    private fun writeHoots(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        value: PetSettings,
    ) {
        preferences[HOOTS_GRAVITY] = value.gravity
        preferences[HOOTS_TILT_GRAVITY] = value.tiltGravity
        preferences[HOOTS_SHAKE_DETONATION] = value.shakeDetonation
        preferences[HOOTS_KEEP_UPRIGHT] = value.keepUpright
        preferences[HOOTS_AVOID_KEYBOARD] = value.avoidKeyboard
        preferences[HOOTS_PHYSICS_MULTIPLIER] = value.physicsMultiplier
        preferences[HOOTS_SIZE_PERCENT] = value.sizePercent
    }

    companion object {
        private val GRAVITY = booleanPreferencesKey("gravity")
        private val TILT_GRAVITY = booleanPreferencesKey("tilt_gravity")
        private val SHAKE_DETONATION = booleanPreferencesKey("shake_detonation")
        private val KEEP_UPRIGHT = booleanPreferencesKey("keep_upright")
        private val AVOID_KEYBOARD = booleanPreferencesKey("avoid_keyboard")
        private val PHYSICS_MULTIPLIER = doublePreferencesKey("physics_multiplier")
        private val SIZE_PERCENT = doublePreferencesKey("size_percent")
        private val COLLISION = booleanPreferencesKey("collision")
        private val HOOTS_GRAVITY = booleanPreferencesKey("hoots_gravity")
        private val HOOTS_TILT_GRAVITY = booleanPreferencesKey("hoots_tilt_gravity")
        private val HOOTS_SHAKE_DETONATION = booleanPreferencesKey("hoots_shake_detonation")
        private val HOOTS_KEEP_UPRIGHT = booleanPreferencesKey("hoots_keep_upright")
        private val HOOTS_AVOID_KEYBOARD = booleanPreferencesKey("hoots_avoid_keyboard")
        private val HOOTS_PHYSICS_MULTIPLIER = doublePreferencesKey("hoots_physics_multiplier")
        private val HOOTS_SIZE_PERCENT = doublePreferencesKey("hoots_size_percent")
        private val SEEDY_HOOTS_COLLISION = booleanPreferencesKey("seedy_hoots_collision")
    }
}
