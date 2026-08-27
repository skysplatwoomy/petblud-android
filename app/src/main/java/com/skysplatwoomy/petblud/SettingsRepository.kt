package com.skysplatwoomy.petblud

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
            val legacyBubbleOpacity = preferences[HOOTS_BUBBLE_OPACITY_PERCENT]
                ?: PetSettings.DEFAULT_BUBBLE_OPACITY_PERCENT
            val legacyBorderColor = preferences[HOOTS_BUBBLE_BORDER_COLOR]
                ?: PetSettings.DEFAULT_BUBBLE_BORDER_COLOR
            val legacyFillColor = preferences[HOOTS_BUBBLE_FILL_COLOR]
                ?: PetSettings.DEFAULT_BUBBLE_FILL_COLOR
            val legacyTextOpacity = preferences[HOOTS_BUBBLE_TEXT_OPACITY_PERCENT]
                ?: PetSettings.DEFAULT_BUBBLE_OPACITY_PERCENT
            val legacyAppearance = BubbleAppearance(
                borderColor = legacyBorderColor,
                fillColor = legacyFillColor,
                textColor = BubbleAppearance.contrastTextColor(legacyFillColor),
                borderOpacityPercent = preferences[HOOTS_BUBBLE_BORDER_OPACITY_PERCENT]
                    ?: legacyBubbleOpacity,
                fillOpacityPercent = preferences[HOOTS_BUBBLE_FILL_OPACITY_PERCENT]
                    ?: legacyBubbleOpacity,
                textOpacityPercent = legacyTextOpacity,
            )
            val seedy = PetSettings(
                gravity = preferences[GRAVITY] ?: true,
                tiltGravity = preferences[TILT_GRAVITY] ?: false,
                shakeDetonation = preferences[SHAKE_DETONATION] ?: false,
                keepUpright = preferences[KEEP_UPRIGHT] ?: false,
                avoidKeyboard = preferences[AVOID_KEYBOARD] ?: false,
                physicsMultiplier = preferences[PHYSICS_MULTIPLIER] ?: 1.0,
                sizePercent = preferences[SIZE_PERCENT] ?: 100.0,
                collision = preferences[COLLISION] ?: false,
                disablePhysicsInteractions = false,
                showChatGptNotifications = false,
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
                disablePhysicsInteractions = preferences[HOOTS_DISABLE_PHYSICS_INTERACTIONS] ?: false,
                showChatGptNotifications = preferences[HOOTS_SHOW_CHATGPT_NOTIFICATIONS] ?: false,
                bubbleColorMode = BubbleColorMode.fromWireName(preferences[HOOTS_BUBBLE_COLOR_MODE]),
                bubbleDarkAppearance = BubbleAppearance(
                    borderColor = preferences[HOOTS_BUBBLE_DARK_BORDER_COLOR]
                        ?: BubbleAppearance.DEFAULT_BORDER_COLOR,
                    fillColor = preferences[HOOTS_BUBBLE_DARK_FILL_COLOR]
                        ?: BubbleAppearance.DEFAULT_DARK_FILL_COLOR,
                    textColor = preferences[HOOTS_BUBBLE_DARK_TEXT_COLOR]
                        ?: BubbleAppearance.DEFAULT_DARK_TEXT_COLOR,
                    borderOpacityPercent = preferences[HOOTS_BUBBLE_DARK_BORDER_OPACITY_PERCENT]
                        ?: BubbleAppearance.DEFAULT_OPACITY_PERCENT,
                    fillOpacityPercent = preferences[HOOTS_BUBBLE_DARK_FILL_OPACITY_PERCENT]
                        ?: BubbleAppearance.DEFAULT_OPACITY_PERCENT,
                    textOpacityPercent = preferences[HOOTS_BUBBLE_DARK_TEXT_OPACITY_PERCENT]
                        ?: BubbleAppearance.DEFAULT_OPACITY_PERCENT,
                ),
                bubbleLightAppearance = BubbleAppearance(
                    borderColor = preferences[HOOTS_BUBBLE_LIGHT_BORDER_COLOR]
                        ?: legacyAppearance.borderColor,
                    fillColor = preferences[HOOTS_BUBBLE_LIGHT_FILL_COLOR]
                        ?: legacyAppearance.fillColor,
                    textColor = preferences[HOOTS_BUBBLE_LIGHT_TEXT_COLOR]
                        ?: legacyAppearance.textColor,
                    borderOpacityPercent = preferences[HOOTS_BUBBLE_LIGHT_BORDER_OPACITY_PERCENT]
                        ?: legacyAppearance.borderOpacityPercent,
                    fillOpacityPercent = preferences[HOOTS_BUBBLE_LIGHT_FILL_OPACITY_PERCENT]
                        ?: legacyAppearance.fillOpacityPercent,
                    textOpacityPercent = preferences[HOOTS_BUBBLE_LIGHT_TEXT_OPACITY_PERCENT]
                        ?: legacyAppearance.textOpacityPercent,
                ),
                bubbleTapOpensChatGpt = preferences[HOOTS_BUBBLE_TAP_OPENS_CHATGPT] ?: false,
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

    suspend fun setHootsChatGptNotifications(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[HOOTS_SHOW_CHATGPT_NOTIFICATIONS] = enabled
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
        preferences[HOOTS_DISABLE_PHYSICS_INTERACTIONS] = value.disablePhysicsInteractions
        preferences[HOOTS_SHOW_CHATGPT_NOTIFICATIONS] = value.showChatGptNotifications
        preferences[HOOTS_BUBBLE_COLOR_MODE] = value.bubbleColorMode.wireName
        writeBubbleAppearance(preferences, value.bubbleDarkAppearance, dark = true)
        writeBubbleAppearance(preferences, value.bubbleLightAppearance, dark = false)
        preferences[HOOTS_BUBBLE_BORDER_COLOR] = value.bubbleLightAppearance.borderColor
        preferences[HOOTS_BUBBLE_FILL_COLOR] = value.bubbleLightAppearance.fillColor
        preferences[HOOTS_BUBBLE_BORDER_OPACITY_PERCENT] =
            value.bubbleLightAppearance.borderOpacityPercent
        preferences[HOOTS_BUBBLE_FILL_OPACITY_PERCENT] =
            value.bubbleLightAppearance.fillOpacityPercent
        preferences[HOOTS_BUBBLE_TEXT_OPACITY_PERCENT] =
            value.bubbleLightAppearance.textOpacityPercent
        preferences[HOOTS_BUBBLE_TAP_OPENS_CHATGPT] = value.bubbleTapOpensChatGpt
    }

    private fun writeBubbleAppearance(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        appearance: BubbleAppearance,
        dark: Boolean,
    ) {
        if (dark) {
            preferences[HOOTS_BUBBLE_DARK_BORDER_COLOR] = appearance.borderColor
            preferences[HOOTS_BUBBLE_DARK_FILL_COLOR] = appearance.fillColor
            preferences[HOOTS_BUBBLE_DARK_TEXT_COLOR] = appearance.textColor
            preferences[HOOTS_BUBBLE_DARK_BORDER_OPACITY_PERCENT] = appearance.borderOpacityPercent
            preferences[HOOTS_BUBBLE_DARK_FILL_OPACITY_PERCENT] = appearance.fillOpacityPercent
            preferences[HOOTS_BUBBLE_DARK_TEXT_OPACITY_PERCENT] = appearance.textOpacityPercent
        } else {
            preferences[HOOTS_BUBBLE_LIGHT_BORDER_COLOR] = appearance.borderColor
            preferences[HOOTS_BUBBLE_LIGHT_FILL_COLOR] = appearance.fillColor
            preferences[HOOTS_BUBBLE_LIGHT_TEXT_COLOR] = appearance.textColor
            preferences[HOOTS_BUBBLE_LIGHT_BORDER_OPACITY_PERCENT] = appearance.borderOpacityPercent
            preferences[HOOTS_BUBBLE_LIGHT_FILL_OPACITY_PERCENT] = appearance.fillOpacityPercent
            preferences[HOOTS_BUBBLE_LIGHT_TEXT_OPACITY_PERCENT] = appearance.textOpacityPercent
        }
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
        private val HOOTS_DISABLE_PHYSICS_INTERACTIONS =
            booleanPreferencesKey("hoots_disable_physics_interactions")
        private val HOOTS_SHOW_CHATGPT_NOTIFICATIONS =
            booleanPreferencesKey("hoots_show_chatgpt_notifications")
        private val HOOTS_BUBBLE_BORDER_COLOR = intPreferencesKey("hoots_bubble_border_color")
        private val HOOTS_BUBBLE_FILL_COLOR = intPreferencesKey("hoots_bubble_fill_color")
        private val HOOTS_BUBBLE_OPACITY_PERCENT =
            doublePreferencesKey("hoots_bubble_opacity_percent")
        private val HOOTS_BUBBLE_BORDER_OPACITY_PERCENT =
            doublePreferencesKey("hoots_bubble_border_opacity_percent")
        private val HOOTS_BUBBLE_FILL_OPACITY_PERCENT =
            doublePreferencesKey("hoots_bubble_fill_opacity_percent")
        private val HOOTS_BUBBLE_TEXT_OPACITY_PERCENT =
            doublePreferencesKey("hoots_bubble_text_opacity_percent")
        private val HOOTS_BUBBLE_TAP_OPENS_CHATGPT =
            booleanPreferencesKey("hoots_bubble_tap_opens_chatgpt")
        private val HOOTS_BUBBLE_COLOR_MODE = stringPreferencesKey("hoots_bubble_color_mode")
        private val HOOTS_BUBBLE_DARK_BORDER_COLOR =
            intPreferencesKey("hoots_bubble_dark_border_color")
        private val HOOTS_BUBBLE_DARK_FILL_COLOR =
            intPreferencesKey("hoots_bubble_dark_fill_color")
        private val HOOTS_BUBBLE_DARK_TEXT_COLOR =
            intPreferencesKey("hoots_bubble_dark_text_color")
        private val HOOTS_BUBBLE_DARK_BORDER_OPACITY_PERCENT =
            doublePreferencesKey("hoots_bubble_dark_border_opacity_percent")
        private val HOOTS_BUBBLE_DARK_FILL_OPACITY_PERCENT =
            doublePreferencesKey("hoots_bubble_dark_fill_opacity_percent")
        private val HOOTS_BUBBLE_DARK_TEXT_OPACITY_PERCENT =
            doublePreferencesKey("hoots_bubble_dark_text_opacity_percent")
        private val HOOTS_BUBBLE_LIGHT_BORDER_COLOR =
            intPreferencesKey("hoots_bubble_light_border_color")
        private val HOOTS_BUBBLE_LIGHT_FILL_COLOR =
            intPreferencesKey("hoots_bubble_light_fill_color")
        private val HOOTS_BUBBLE_LIGHT_TEXT_COLOR =
            intPreferencesKey("hoots_bubble_light_text_color")
        private val HOOTS_BUBBLE_LIGHT_BORDER_OPACITY_PERCENT =
            doublePreferencesKey("hoots_bubble_light_border_opacity_percent")
        private val HOOTS_BUBBLE_LIGHT_FILL_OPACITY_PERCENT =
            doublePreferencesKey("hoots_bubble_light_fill_opacity_percent")
        private val HOOTS_BUBBLE_LIGHT_TEXT_OPACITY_PERCENT =
            doublePreferencesKey("hoots_bubble_light_text_opacity_percent")
        private val SEEDY_HOOTS_COLLISION = booleanPreferencesKey("seedy_hoots_collision")
    }
}
