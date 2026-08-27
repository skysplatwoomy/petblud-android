package com.skysplatwoomy.petblud

import kotlin.math.pow

enum class BubbleColorMode {
    DARK,
    LIGHT,
    SYSTEM;

    val wireName: String
        get() = name.lowercase()

    fun resolvedPreset(systemIsDark: Boolean): BubbleColorPreset = when (this) {
        DARK -> BubbleColorPreset.DARK
        LIGHT -> BubbleColorPreset.LIGHT
        SYSTEM -> if (systemIsDark) BubbleColorPreset.DARK else BubbleColorPreset.LIGHT
    }

    companion object {
        fun fromWireName(value: String?): BubbleColorMode =
            entries.firstOrNull { it.wireName == value } ?: LIGHT
    }
}

enum class BubbleColorPreset {
    DARK,
    LIGHT,
}

data class BubbleAppearance(
    val borderColor: Int,
    val fillColor: Int,
    val textColor: Int,
    val borderOpacityPercent: Double = DEFAULT_OPACITY_PERCENT,
    val fillOpacityPercent: Double = DEFAULT_OPACITY_PERCENT,
    val textOpacityPercent: Double = DEFAULT_OPACITY_PERCENT,
) {
    fun normalized(): BubbleAppearance = copy(
        borderColor = opaqueColor(borderColor),
        fillColor = opaqueColor(fillColor),
        textColor = opaqueColor(textColor),
        borderOpacityPercent = normalizeOpacity(borderOpacityPercent),
        fillOpacityPercent = normalizeOpacity(fillOpacityPercent),
        textOpacityPercent = normalizeOpacity(textOpacityPercent),
    )

    companion object {
        const val DEFAULT_BORDER_COLOR: Int = -681411
        const val DEFAULT_LIGHT_FILL_COLOR: Int = -3385
        const val DEFAULT_LIGHT_TEXT_COLOR: Int = 0xFF0B1118.toInt()
        const val DEFAULT_DARK_FILL_COLOR: Int = 0xFF151D24.toInt()
        const val DEFAULT_DARK_TEXT_COLOR: Int = 0xFFFFF2C7.toInt()
        const val DEFAULT_OPACITY_PERCENT = 100.0
        const val MIN_OPACITY_PERCENT = 0.0
        const val MAX_OPACITY_PERCENT = 100.0

        fun lightDefault() = BubbleAppearance(
            borderColor = DEFAULT_BORDER_COLOR,
            fillColor = DEFAULT_LIGHT_FILL_COLOR,
            textColor = DEFAULT_LIGHT_TEXT_COLOR,
        )

        fun darkDefault() = BubbleAppearance(
            borderColor = DEFAULT_BORDER_COLOR,
            fillColor = DEFAULT_DARK_FILL_COLOR,
            textColor = DEFAULT_DARK_TEXT_COLOR,
        )

        fun contrastTextColor(background: Int): Int =
            if (relativeLuminance(background) < 0.42) 0xFFFFFFFF.toInt() else DEFAULT_LIGHT_TEXT_COLOR

        private fun opaqueColor(color: Int): Int = color or (0xFF shl 24)

        private fun normalizeOpacity(value: Double): Double =
            value.coerceIn(MIN_OPACITY_PERCENT, MAX_OPACITY_PERCENT)

        private fun relativeLuminance(color: Int): Double {
            fun channel(shift: Int): Double {
                val value = ((color shr shift) and 0xFF) / 255.0
                return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
    }
}

data class PetSettings(
    val gravity: Boolean = true,
    val tiltGravity: Boolean = false,
    val shakeDetonation: Boolean = false,
    val keepUpright: Boolean = false,
    val avoidKeyboard: Boolean = false,
    val physicsMultiplier: Double = 1.0,
    val sizePercent: Double = 100.0,
    val collision: Boolean = false,
    val disablePhysicsInteractions: Boolean = false,
    val showChatGptNotifications: Boolean = false,
    val bubbleColorMode: BubbleColorMode = BubbleColorMode.LIGHT,
    val bubbleDarkAppearance: BubbleAppearance = BubbleAppearance.darkDefault(),
    val bubbleLightAppearance: BubbleAppearance = BubbleAppearance.lightDefault(),
    val bubbleTapOpensChatGpt: Boolean = false,
) {
    fun normalized(): PetSettings = copy(
        physicsMultiplier = physicsMultiplier.coerceIn(MIN_PHYSICS_MULTIPLIER, MAX_PHYSICS_MULTIPLIER),
        sizePercent = sizePercent.coerceIn(MIN_SIZE_PERCENT, MAX_SIZE_PERCENT),
        bubbleDarkAppearance = bubbleDarkAppearance.normalized(),
        bubbleLightAppearance = bubbleLightAppearance.normalized(),
    )

    fun bubbleAppearance(systemIsDark: Boolean): BubbleAppearance = when (
        bubbleColorMode.resolvedPreset(systemIsDark)
    ) {
        BubbleColorPreset.DARK -> bubbleDarkAppearance
        BubbleColorPreset.LIGHT -> bubbleLightAppearance
    }

    companion object {
        const val MIN_PHYSICS_MULTIPLIER = 0.1
        const val MAX_PHYSICS_MULTIPLIER = 5.0
        const val MIN_SIZE_PERCENT = 10.0
        const val MAX_SIZE_PERCENT = 500.0
        const val DEFAULT_BUBBLE_BORDER_COLOR = BubbleAppearance.DEFAULT_BORDER_COLOR
        const val DEFAULT_BUBBLE_FILL_COLOR = BubbleAppearance.DEFAULT_LIGHT_FILL_COLOR
        const val DEFAULT_BUBBLE_TEXT_COLOR = BubbleAppearance.DEFAULT_LIGHT_TEXT_COLOR
        const val DEFAULT_BUBBLE_OPACITY_PERCENT = BubbleAppearance.DEFAULT_OPACITY_PERCENT
        const val MIN_BUBBLE_OPACITY_PERCENT = BubbleAppearance.MIN_OPACITY_PERCENT
        const val MAX_BUBBLE_OPACITY_PERCENT = BubbleAppearance.MAX_OPACITY_PERCENT
    }
}

enum class PetSpecies {
    SEEDY,
    HOOTS;

    val wireName: String
        get() = name.lowercase()

    companion object {
        fun fromWireName(value: String?): PetSpecies =
            entries.firstOrNull { it.wireName == value } ?: SEEDY
    }
}

data class AppSettings(
    val seedy: PetSettings = PetSettings(),
    val hoots: PetSettings = PetSettings(),
    val seedyHootsCollision: Boolean = false,
) {
    fun forSpecies(species: PetSpecies): PetSettings = when (species) {
        PetSpecies.SEEDY -> seedy
        PetSpecies.HOOTS -> hoots
    }

    fun withSpecies(species: PetSpecies, settings: PetSettings): AppSettings = when (species) {
        PetSpecies.SEEDY -> copy(seedy = settings.normalized())
        PetSpecies.HOOTS -> copy(hoots = settings.normalized())
    }

    fun normalized(): AppSettings = copy(
        seedy = seedy.normalized(),
        hoots = hoots.normalized(),
    )
}
