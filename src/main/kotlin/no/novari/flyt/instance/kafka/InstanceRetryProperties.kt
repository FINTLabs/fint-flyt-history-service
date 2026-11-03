package no.novari.flyt.instance.kafka

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("novari.flyt.instance.retry")
data class InstanceRetryProperties(
    val sendTimeout: Duration = Duration.ofSeconds(2),
    val maxBatchSize: Int = 20,
) {
    init {
        require(maxBatchSize > 0) { "Retry maximum batch size must be positive" }
        require(sendTimeout.toMillis() > 0) {
            "Retry send timeout must be at least one millisecond"
        }
    }
}
