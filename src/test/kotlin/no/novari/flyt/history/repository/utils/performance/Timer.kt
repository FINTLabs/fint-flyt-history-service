package no.novari.flyt.history.repository.utils.performance

import java.time.Duration

class Timer private constructor(
    private val startTime: Long,
) {
    val elapsedTime: Duration
        get() = Duration.ofNanos(System.nanoTime() - startTime)

    companion object {
        @JvmStatic
        fun start() = Timer(System.nanoTime())
    }
}
