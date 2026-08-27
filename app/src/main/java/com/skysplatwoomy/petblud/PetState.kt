package com.skysplatwoomy.petblud

data class DragSample(
    val timeSeconds: Double,
    val x: Double,
    val y: Double,
)

data class WorldBounds(
    val width: Double,
    val height: Double,
) {
    fun normalized(): WorldBounds = WorldBounds(
        width = width.coerceAtLeast(1.0),
        height = height.coerceAtLeast(1.0),
    )
}

data class PetSnapshot(
    val id: Long,
    val species: PetSpecies,
    val gravityEnabled: Boolean,
    val physicsMultiplier: Double,
    val sizePercent: Double,
    val showChatGptNotifications: Boolean,
)

class PetState internal constructor(
    val id: Long,
    val species: PetSpecies,
    x: Double,
    y: Double,
    settings: PetSettings,
    specialTimer: Double,
) {
    var x: Double = x
    var y: Double = y
    var vx: Double = 260.0
    var vy: Double = 0.0

    var gravityEnabled: Boolean = settings.gravity
    var physicsMultiplier: Double = settings.physicsMultiplier
    var sizePercent: Double = settings.sizePercent

    var dragging: Boolean = false
    var didDrag: Boolean = false
    var dragDx: Double = 0.0
    var dragDy: Double = 0.0
    var pressRootX: Double = 0.0
    var pressRootY: Double = 0.0
    val dragSamples: MutableList<DragSample> = mutableListOf()

    var animation: PetAnimation? = PetAnimation.IDLE
    var frameIndex: Int = 0
    var frameTime: Double = 0.0
    var animationLoop: Boolean = true
    var facing: Int = 1
    var cheerOnLand: Boolean = false
    var pendingOneShot: PetAnimation? = null
    var specialTimer: Double = specialTimer
    var codeTimeRemaining: Double = 0.0

    val width: Double
        get() = BASE_WIDTH * sizePercent / 100.0

    val height: Double
        get() = BASE_HEIGHT * sizePercent / 100.0

    fun snapshot(showChatGptNotifications: Boolean = false): PetSnapshot = PetSnapshot(
        id = id,
        species = species,
        gravityEnabled = gravityEnabled,
        physicsMultiplier = physicsMultiplier,
        sizePercent = sizePercent,
        showChatGptNotifications = showChatGptNotifications,
    )

    companion object {
        const val BASE_WIDTH = 116.0
        const val BASE_HEIGHT = 126.0
    }
}
