package com.skysplatwoomy.petblud

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LiveSaveDebouncer<T>(
    private val scope: CoroutineScope,
    private val delayMillis: Long = 200L,
    private val save: suspend (T) -> Unit,
) {
    private var pending: Job? = null

    fun submit(value: T) {
        pending?.cancel()
        pending = scope.launch {
            delay(delayMillis)
            save(value)
        }
    }

    fun saveNow(value: T) {
        pending?.cancel()
        pending = scope.launch { save(value) }
    }

    fun cancel() {
        pending?.cancel()
        pending = null
    }
}
