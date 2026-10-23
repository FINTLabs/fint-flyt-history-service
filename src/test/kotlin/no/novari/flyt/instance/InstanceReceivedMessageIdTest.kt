package no.novari.flyt.instance

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

class InstanceReceivedMessageIdTest {
    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t\n"])
    fun `blank topics are rejected`(topic: String) {
        assertThatThrownBy { messageId(topic = topic) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Topic must not be blank")
    }

    @Test
    fun `negative partitions are rejected`() {
        assertThatThrownBy { messageId(partition = -1) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Partition must not be negative")
    }

    @Test
    fun `negative offsets are rejected`() {
        assertThatThrownBy { messageId(offset = -1) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Offset must not be negative")
    }

    @Test
    fun `zero partition and offset are valid`() {
        val messageId = messageId()
        assertThat(messageId.partition).isZero()
        assertThat(messageId.offset).isZero()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t\n"])
    fun `blank cluster identifiers are rejected`(clusterId: String) {
        assertThatThrownBy { messageId(clusterId = clusterId) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Cluster ID must not be blank")
    }

    @Test
    fun `unknown topic identifiers are rejected`() {
        assertThatThrownBy { messageId(topicId = UUID(0L, 0L)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Topic ID must not be zero")
    }

    @Test
    fun `offsets that would overflow next offset are rejected`() {
        assertThatThrownBy { messageId(offset = Long.MAX_VALUE) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Offset must allow calculating the next offset")
    }

    private fun messageId(
        topic: String = "instance-received",
        partition: Int = 0,
        offset: Long = 0,
        clusterId: String = "test-cluster",
        topicId: UUID = UUID.fromString("d254590e-616f-4e52-ac28-f84d55bb4281"),
    ): InstanceReceivedMessageId = InstanceReceivedMessageId(topic, partition, offset, clusterId, topicId)
}
