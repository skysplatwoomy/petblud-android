package com.skysplatwoomy.petblud

import android.content.Context
import android.content.Intent

object PetCommands {
    const val ACTION_START = "com.skysplatwoomy.petblud.action.START"
    const val ACTION_SPAWN = "com.skysplatwoomy.petblud.action.SPAWN"
    const val ACTION_STOP = "com.skysplatwoomy.petblud.action.STOP"
    const val ACTION_KILL = "com.skysplatwoomy.petblud.action.KILL"
    const val ACTION_KILL_OTHERS = "com.skysplatwoomy.petblud.action.KILL_OTHERS"
    const val ACTION_KILL_SPECIES = "com.skysplatwoomy.petblud.action.KILL_SPECIES"
    const val ACTION_EXPLODE = "com.skysplatwoomy.petblud.action.EXPLODE"
    const val ACTION_DETONATE = "com.skysplatwoomy.petblud.action.DETONATE"
    const val ACTION_TOGGLE_GRAVITY = "com.skysplatwoomy.petblud.action.TOGGLE_GRAVITY"
    const val ACTION_SET_PET_PHYSICS = "com.skysplatwoomy.petblud.action.SET_PET_PHYSICS"
    const val ACTION_APPLY_SETTINGS = "com.skysplatwoomy.petblud.action.APPLY_SETTINGS"

    const val EXTRA_COUNT = "count"
    const val EXTRA_PET_ID = "pet_id"
    const val EXTRA_ORIGIN_X = "origin_x"
    const val EXTRA_ORIGIN_Y = "origin_y"
    const val EXTRA_PHYSICS_MULTIPLIER = "physics_multiplier"
    const val EXTRA_SPECIES = "species"

    fun start(context: Context, species: PetSpecies = PetSpecies.SEEDY) = dispatch(context, Intent(context, PetOverlayService::class.java).apply {
        action = ACTION_START
        putExtra(EXTRA_SPECIES, species.wireName)
    })

    fun spawn(
        context: Context,
        count: Int,
        species: PetSpecies = PetSpecies.SEEDY,
    ) = dispatch(context, Intent(context, PetOverlayService::class.java).apply {
        action = ACTION_SPAWN
        putExtra(EXTRA_COUNT, count.coerceIn(1, PetManager.MAX_PETS))
        putExtra(EXTRA_SPECIES, species.wireName)
    })

    fun stop(context: Context) = dispatch(context, Intent(context, PetOverlayService::class.java).apply {
        action = ACTION_STOP
    })

    fun send(context: Context, actionName: String, configure: Intent.() -> Unit = {}) =
        dispatch(context, Intent(context, PetOverlayService::class.java).apply {
            action = actionName
            configure()
        })

    fun species(intent: Intent?): PetSpecies =
        PetSpecies.fromWireName(intent?.getStringExtra(EXTRA_SPECIES))

    private fun dispatch(context: Context, intent: Intent) {
        context.startForegroundService(intent)
    }
}
