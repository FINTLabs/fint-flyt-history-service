package no.novari.flyt.instance.kafka

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("novari.flyt.instance.retry")
data class InstanceRetryProperties(
    val sendTimeout: Duration = Duration.ofSeconds(30),
) {
    init {
        require(sendTimeout.toMillis() in 3..Int.MAX_VALUE.toLong()) {
            "Retry send timeout is outside supported limits"
        }
    }
}
