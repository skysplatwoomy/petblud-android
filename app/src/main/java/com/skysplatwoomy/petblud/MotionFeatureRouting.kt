package com.skysplatwoomy.petblud

object MotionFeatureRouting {
    fun tiltSpecies(settings: AppSettings): Set<PetSpecies> =
        PetSpecies.entries.filterTo(linkedSetOf()) { settings.forSpecies(it).tiltGravity }

    fun shakeSpecies(settings: AppSettings): Set<PetSpecies> =
        tiltSpecies(settings).filterTo(linkedSetOf()) {
            settings.forSpecies(it).shakeDetonation
        }
}
