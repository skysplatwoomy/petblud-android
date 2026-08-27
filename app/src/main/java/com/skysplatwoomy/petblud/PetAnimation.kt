package com.skysplatwoomy.petblud

enum class PetAnimation(
    val row: Int,
    val frameCount: Int,
    val framesPerSecond: Double,
    val oneShot: Boolean = false,
) {
    IDLE(0, 7, 6.0),
    WALK_RIGHT(1, 8, 10.0),
    WALK_LEFT(2, 8, 10.0),
    WAVE(3, 4, 8.0, oneShot = true),
    JUMP(4, 5, 12.0),
    DIZZY(5, 8, 10.0, oneShot = true),
    EAT(6, 6, 7.0, oneShot = true),
    CODE(7, 6, 6.0, oneShot = true),
    CHEER(8, 6, 10.0, oneShot = true),
    FALL(9, 8, 12.0),
    SPIN(10, 8, 14.0, oneShot = true),
}
