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
) {
    fun normalized(): PetSettings = copy(
        physicsMultiplier = physicsMultiplier.coerceIn(MIN_PHYSICS_MULTIPLIER, MAX_PHYSICS_MULTIPLIER),
        sizePercent = sizePercent.coerceIn(MIN_SIZE_PERCENT, MAX_SIZE_PERCENT),
    )

    companion object {
        const val MIN_PHYSICS_MULTIPLIER = 0.1
        const val MAX_PHYSICS_MULTIPLIER = 5.0
        const val MIN_SIZE_PERCENT = 10.0
        const val MAX_SIZE_PERCENT = 500.0
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
