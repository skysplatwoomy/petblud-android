package com.skysplatwoomy.petblud

import kotlin.math.atan2

internal object MotionGravityMapper {
    const val ROTATION_0 = 0
    const val ROTATION_90 = 1
    const val ROTATION_180 = 2
    const val ROTATION_270 = 3

    fun toScreen(rawX: Double, rawY: Double, displayRotation: Int): Pair<Double, Double> =
        when (displayRotation) {
            ROTATION_90 -> rawY to rawX
            ROTATION_180 -> rawX to -rawY
            ROTATION_270 -> -rawY to -rawX
            else -> -rawX to rawY
        }

    fun spriteRotationDegrees(gravityX: Double, gravityY: Double): Float =
        Math.toDegrees(atan2(-gravityX, gravityY)).toFloat()
}
