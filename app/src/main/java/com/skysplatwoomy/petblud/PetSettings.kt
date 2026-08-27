package com.skysplatwoomy.petblud

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
    val bubbleBorderColor: Int = DEFAULT_BUBBLE_BORDER_COLOR,
    val bubbleFillColor: Int = DEFAULT_BUBBLE_FILL_COLOR,
    val bubbleBorderOpacityPercent: Double = DEFAULT_BUBBLE_OPACITY_PERCENT,
    val bubbleFillOpacityPercent: Double = DEFAULT_BUBBLE_OPACITY_PERCENT,
    val bubbleTextOpacityPercent: Double = DEFAULT_BUBBLE_OPACITY_PERCENT,
    val bubbleTapOpensChatGpt: Boolean = false,
) {
    fun normalized(): PetSettings = copy(
        physicsMultiplier = physicsMultiplier.coerceIn(MIN_PHYSICS_MULTIPLIER, MAX_PHYSICS_MULTIPLIER),
        sizePercent = sizePercent.coerceIn(MIN_SIZE_PERCENT, MAX_SIZE_PERCENT),
        bubbleBorderColor = opaqueColor(bubbleBorderColor),
        bubbleFillColor = opaqueColor(bubbleFillColor),
        bubbleBorderOpacityPercent = bubbleBorderOpacityPercent.coerceIn(
            MIN_BUBBLE_OPACITY_PERCENT,
            MAX_BUBBLE_OPACITY_PERCENT,
        ),
        bubbleFillOpacityPercent = bubbleFillOpacityPercent.coerceIn(
            MIN_BUBBLE_OPACITY_PERCENT,
            MAX_BUBBLE_OPACITY_PERCENT,
        ),
        bubbleTextOpacityPercent = bubbleTextOpacityPercent.coerceIn(
            MIN_BUBBLE_OPACITY_PERCENT,
            MAX_BUBBLE_OPACITY_PERCENT,
        ),
    )

    companion object {
        const val MIN_PHYSICS_MULTIPLIER = 0.1
        const val MAX_PHYSICS_MULTIPLIER = 5.0
        const val MIN_SIZE_PERCENT = 10.0
        const val MAX_SIZE_PERCENT = 500.0
        const val DEFAULT_BUBBLE_BORDER_COLOR: Int = -681411
        const val DEFAULT_BUBBLE_FILL_COLOR: Int = -3385
        const val DEFAULT_BUBBLE_OPACITY_PERCENT = 100.0
        const val MIN_BUBBLE_OPACITY_PERCENT = 0.0
        const val MAX_BUBBLE_OPACITY_PERCENT = 100.0

        private fun opaqueColor(color: Int): Int = color or (0xFF shl 24)
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
