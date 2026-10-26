package no.novari.flyt.instance.outbox

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("novari.flyt.instance.outbox")
data class InstanceRegisteredOutboxProperties(
    val pollDelay: Duration = Duration.ofSeconds(1),
    val maxRecordsPerRun: Int = 100,
    val sendTimeout: Duration = Duration.ofSeconds(30),
    val claimDuration: Duration = Duration.ofMinutes(2),
    val retryInitialDelay: Duration = Duration.ofSeconds(5),
    val retryMaxDelay: Duration = Duration.ofMinutes(5),
) {
    init {
        require(pollDelay.toMillis() > 0) { "Outbox poll delay must be positive" }
        require(maxRecordsPerRun > 0) { "Outbox max records per run must be positive" }
        require(
            sendTimeout.toMillis() in 3..Int.MAX_VALUE.toLong(),
        ) { "Outbox send timeout is outside supported limits" }
        require(
            claimDuration > sendTimeout.multipliedBy(2),
        ) { "Outbox claim duration must exceed twice the send timeout" }
        require(retryInitialDelay.toMillis() > 0) { "Outbox retry delay must be positive" }
        require(retryMaxDelay >= retryInitialDelay) { "Outbox maximum retry delay must not be less than initial delay" }
    }

    fun retryDelay(attempt: Int): Duration {
        var delay = retryInitialDelay.toMillis()
        val maximum = retryMaxDelay.toMillis()
        repeat((attempt - 1).coerceIn(0, 63)) {
            delay = if (delay >= maximum - delay) maximum else delay * 2
        }
        return Duration.ofMillis(delay.coerceAtMost(maximum))
    }
}
