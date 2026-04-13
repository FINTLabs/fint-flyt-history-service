package no.novari.flyt.history.repository.utils.performance

import java.time.Duration
import java.util.StringJoiner

class PageSizePerformanceTestCase(
    val requestedMaxSize: Int,
    val expectedSize: Int,
    val maxElapsedTime: Duration,
) {
    override fun toString(): String {
        val joiner = StringJoiner(", ", "(", ")")
        joiner.add("requestedSize=$requestedMaxSize")
        joiner.add("expectedSize=$expectedSize")
        joiner.add("maxElapsedTime=$maxElapsedTime")
        return joiner.toString()
    }
}
