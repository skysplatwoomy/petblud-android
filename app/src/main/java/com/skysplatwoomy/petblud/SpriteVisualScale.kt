package com.skysplatwoomy.petblud

object SpriteVisualScale {
    fun forAnimation(species: PetSpecies, animation: PetAnimation): Float =
        if (species == PetSpecies.HOOTS && animation == PetAnimation.IDLE) {
            HOOTS_IDLE_SCALE
        } else {
            1f
        }

    const val HOOTS_IDLE_SCALE = 0.90f
}
