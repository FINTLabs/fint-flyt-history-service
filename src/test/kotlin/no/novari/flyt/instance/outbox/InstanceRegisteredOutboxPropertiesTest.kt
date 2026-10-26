package no.novari.flyt.instance.outbox

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration

class InstanceRegisteredOutboxPropertiesTest {
    @Test
    fun `retry delays grow exponentially and remain bounded even for large attempt counts`() {
        val properties = InstanceRegisteredOutboxProperties()
        assertThat(properties.retryDelay(1)).isEqualTo(Duration.ofSeconds(5))
        assertThat(properties.retryDelay(2)).isEqualTo(Duration.ofSeconds(10))
        assertThat(properties.retryDelay(7)).isEqualTo(Duration.ofMinutes(5))
        assertThat(properties.retryDelay(Int.MAX_VALUE)).isEqualTo(Duration.ofMinutes(5))
    }

    @Test
    fun `claim must outlast a complete bounded publication attempt`() {
        assertThatThrownBy { InstanceRegisteredOutboxProperties(claimDuration = Duration.ofSeconds(60)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `invalid polling and retry configuration is rejected`() {
        assertThatThrownBy { InstanceRegisteredOutboxProperties(maxRecordsPerRun = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { InstanceRegisteredOutboxProperties(pollDelay = Duration.ZERO) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { InstanceRegisteredOutboxProperties(retryInitialDelay = Duration.ZERO) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { InstanceRegisteredOutboxProperties(retryMaxDelay = Duration.ofSeconds(1)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
