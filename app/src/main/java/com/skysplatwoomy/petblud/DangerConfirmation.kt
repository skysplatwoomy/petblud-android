package com.skysplatwoomy.petblud

class DangerConfirmation(private val timeoutMillis: Long = 3_000L) {
    private var armedAtMillis: Long? = null

    fun tap(nowMillis: Long): Boolean {
        val armedAt = armedAtMillis
        return if (armedAt != null && nowMillis - armedAt in 0..timeoutMillis) {
            armedAtMillis = null
            true
        } else {
            armedAtMillis = nowMillis
            false
        }
    }

    fun reset() {
        armedAtMillis = null
    }
}
