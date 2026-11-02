package no.novari.flyt.instance.kafka

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("novari.flyt.instance.kafka")
data class InstanceReceivedKafkaProperties(
    val metadataTimeout: Duration = Duration.ofSeconds(10),
    val retryAttempts: Long = 3,
    val retryInterval: Duration = Duration.ofSeconds(5),
) {
    init {
        require(retryAttempts in 0 until Long.MAX_VALUE) { "Retry attempts must be finite and not negative" }
        require(retryInterval.toMillis() > 0) { "Retry interval must be positive" }
        require(metadataTimeout.toMillis() in 1..Int.MAX_VALUE.toLong()) {
            "Instance-received metadata timeout is outside supported limits"
        }
    }
}
