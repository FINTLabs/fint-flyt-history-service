package no.novari.flyt.instance.kafka

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("novari.flyt.instance.kafka")
data class InstanceReceivedKafkaProperties(
    val metadataTimeout: Duration = Duration.ofSeconds(10),
) {
    init {
        require(metadataTimeout.toMillis() in 1..Int.MAX_VALUE.toLong()) {
            "Instance-received metadata timeout is outside supported limits"
        }
    }
}
