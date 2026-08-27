package com.skysplatwoomy.petblud

import kotlin.math.sqrt

internal class ShakeDetector {
    private val gravityEstimate = DoubleArray(3)
    private var initialized = false
    private var lastDetonationNanos = Long.MIN_VALUE

    fun addAccelerometerSample(
        x: Double,
        y: Double,
        z: Double,
        timestampNanos: Long,
    ): Double? {
        val sample = doubleArrayOf(x, y, z)
        if (!initialized) {
            sample.copyInto(gravityEstimate)
            initialized = true
            return null
        }

        var linearMagnitudeSquared = 0.0
        for (index in sample.indices) {
            gravityEstimate[index] =
                FILTER_ALPHA * gravityEstimate[index] + (1.0 - FILTER_ALPHA) * sample[index]
            val linearAcceleration = sample[index] - gravityEstimate[index]
            linearMagnitudeSquared += linearAcceleration * linearAcceleration
        }
        val linearMagnitude = sqrt(linearMagnitudeSquared)
        if (linearMagnitude < TRIGGER_ACCELERATION) return null
        if (
            lastDetonationNanos != Long.MIN_VALUE &&
            timestampNanos - lastDetonationNanos < COOLDOWN_NANOS
        ) {
            return null
        }

        lastDetonationNanos = timestampNanos
        return (BASE_STRENGTH + linearMagnitude / STRENGTH_DIVISOR)
            .coerceIn(MIN_STRENGTH, MAX_STRENGTH)
    }

    fun reset() {
        gravityEstimate.fill(0.0)
        initialized = false
        lastDetonationNanos = Long.MIN_VALUE
    }

    companion object {
        const val FILTER_ALPHA = 0.82
        const val TRIGGER_ACCELERATION = 12.0
        const val COOLDOWN_NANOS = 700_000_000L
        const val BASE_STRENGTH = 0.75
        const val STRENGTH_DIVISOR = 12.0
        const val MIN_STRENGTH = 1.5
        const val MAX_STRENGTH = 4.0
    }
}
