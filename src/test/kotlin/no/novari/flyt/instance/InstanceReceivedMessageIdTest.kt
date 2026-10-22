package no.novari.flyt.instance

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class InstanceReceivedMessageIdTest {
    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t\n"])
    fun `blank topics are rejected`(topic: String) {
        assertThatThrownBy { InstanceReceivedMessageId(topic, 0, 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Topic must not be blank")
    }

    @Test
    fun `negative partitions are rejected`() {
        assertThatThrownBy { InstanceReceivedMessageId("instance-received", -1, 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Partition must not be negative")
    }

    @Test
    fun `negative offsets are rejected`() {
        assertThatThrownBy { InstanceReceivedMessageId("instance-received", 0, -1) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Offset must not be negative")
    }

    @Test
    fun `zero partition and offset are valid`() {
        val messageId = InstanceReceivedMessageId("instance-received", 0, 0)
        assertThat(messageId.partition).isZero()
        assertThat(messageId.offset).isZero()
    }
}
