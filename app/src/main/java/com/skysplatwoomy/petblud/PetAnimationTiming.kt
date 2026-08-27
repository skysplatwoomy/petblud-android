package com.skysplatwoomy.petblud

object PetAnimationTiming {
    const val LAPTOP_ACTIVITY_SECONDS = 10.0
    const val HOOTS_READING_FRAME_SECONDS = 0.15
    const val HOOTS_COFFEE_FRAME_INDEX = 2
    const val HOOTS_COFFEE_FRAME_SECONDS = 0.25

    fun activityDuration(species: PetSpecies, animation: PetAnimation): Double = when {
        animation == PetAnimation.CODE -> LAPTOP_ACTIVITY_SECONDS
        species == PetSpecies.HOOTS && animation == PetAnimation.CHEER ->
            LAPTOP_ACTIVITY_SECONDS
        else -> 0.0
    }

    fun frameDuration(
        species: PetSpecies,
        animation: PetAnimation,
        frameIndex: Int,
    ): Double = when {
        species == PetSpecies.HOOTS && animation == PetAnimation.CHEER ->
            HOOTS_READING_FRAME_SECONDS
        species == PetSpecies.HOOTS &&
            animation == PetAnimation.CODE &&
            frameIndex == HOOTS_COFFEE_FRAME_INDEX -> HOOTS_COFFEE_FRAME_SECONDS
        else -> 1.0 / animation.framesPerSecond.coerceAtLeast(1.0)
    }
}
