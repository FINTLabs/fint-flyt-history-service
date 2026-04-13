package no.novari.flyt.history.repository.utils.performance

import java.time.Duration
import java.util.StringJoiner

object DurationFormatter {
    @JvmStatic
    fun formatDuration(duration: Duration): String {
        val stringJoiner = StringJoiner(" ")
        performIfPositive(duration.toMinutes()) { value -> stringJoiner.add(String.format("%2dm", value)) }
        performIfPositive(duration.toSecondsPart().toLong()) { value -> stringJoiner.add(String.format("%2ds", value)) }
        performIfPositive(duration.toMillisPart().toLong()) { value -> stringJoiner.add(String.format("%3dms", value)) }
        return stringJoiner.toString()
    }

    private fun performIfPositive(
        value: Long,
        valueConsumer: (Long) -> Unit,
    ) {
        if (value > 0) {
            valueConsumer(value)
        }
    }
}
