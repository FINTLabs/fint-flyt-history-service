package no.novari.flyt.instance.kafka

import no.novari.flyt.instance.InstanceReceivedOffsetException
import no.novari.flyt.instance.RECEIVED_CLUSTER
import no.novari.flyt.instance.RECEIVED_TOPIC
import no.novari.flyt.instance.RECEIVED_TOPIC_ID
import no.novari.flyt.instance.receivedConsumerConfig
import no.novari.kafka.topic.name.TopicNameService
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.DescribeClusterResult
import org.apache.kafka.clients.admin.DescribeTopicsResult
import org.apache.kafka.clients.admin.ListOffsetsOptions
import org.apache.kafka.clients.admin.ListOffsetsResult
import org.apache.kafka.clients.admin.OffsetSpec
import org.apache.kafka.clients.admin.TopicDescription
import org.apache.kafka.common.IsolationLevel
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.common.Node
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.TopicPartitionInfo
import org.apache.kafka.common.Uuid
import org.apache.kafka.common.errors.TopicAuthorizationException
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException
import org.apache.kafka.common.internals.KafkaFutureImpl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.util.Optional
import java.util.concurrent.TimeoutException

class InstanceReceivedKafkaMetadataServiceTest {
    private val admin: Admin = mock()
    private val cluster: DescribeClusterResult = mock()
    private val names: TopicNameService = mock()
    private val partition = TopicPartition(RECEIVED_TOPIC, 0)

    @BeforeEach
    fun metadata() {
        whenever(names.validateAndMapToTopicName(any())).thenReturn(RECEIVED_TOPIC)
        whenever(admin.describeCluster()).thenReturn(cluster)
        whenever(cluster.clusterId()).thenReturn(KafkaFuture.completedFuture(RECEIVED_CLUSTER))
        describe(Uuid(RECEIVED_TOPIC_ID.mostSignificantBits, RECEIVED_TOPIC_ID.leastSignificantBits))
        whenever(admin.listOffsets(any<Map<TopicPartition, OffsetSpec>>(), any<ListOffsetsOptions>())).thenAnswer {
            val request = it.getArgument<Map<TopicPartition, OffsetSpec>>(0)
            val offset = if (request.values.first().javaClass == OffsetSpec.earliest().javaClass) 5L else 20L
            ListOffsetsResult(
                mapOf(
                    partition to
                        KafkaFuture.completedFuture(
                            ListOffsetsResult.ListOffsetsResultInfo(offset, -1, Optional.empty()),
                        ),
                ),
            )
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["read_committed", "read_uncommitted"])
    fun `metadata uses the topic generation and consumer isolation and closes the client`(isolation: String) {
        val metadata = service(isolation).read()

        assertThat(metadata.clusterId).isEqualTo(RECEIVED_CLUSTER)
        assertThat(metadata.topicId).isEqualTo(RECEIVED_TOPIC_ID)
        assertThat(metadata.partitions).containsEntry(0, InstanceReceivedPartitionOffsets(5, 20))
        val options = argumentCaptor<ListOffsetsOptions>()
        verify(admin, times(2)).listOffsets(any<Map<TopicPartition, OffsetSpec>>(), options.capture())
        val expected =
            if (isolation ==
                "read_committed"
            ) {
                IsolationLevel.READ_COMMITTED
            } else {
                IsolationLevel.READ_UNCOMMITTED
            }
        assertThat(options.allValues.map { it.isolationLevel() }).containsOnly(expected)
        verify(admin).close()
    }

    @Test
    fun `authorization failure is a technical offset error and closes the client`() {
        val failure = KafkaFutureImpl<String>()
        failure.completeExceptionally(TopicAuthorizationException("access denied"))
        whenever(cluster.clusterId()).thenReturn(failure)

        assertThatThrownBy { service().read() }
            .isInstanceOf(InstanceReceivedOffsetException::class.java)
            .hasRootCauseInstanceOf(TopicAuthorizationException::class.java)
        verify(admin).close()
    }

    @Test
    fun `metadata lookup is bounded and a timeout closes the client`() {
        whenever(cluster.clusterId()).thenReturn(KafkaFutureImpl())

        assertThatThrownBy { service().read() }
            .isInstanceOf(InstanceReceivedOffsetException::class.java)
            .hasCauseInstanceOf(TimeoutException::class.java)
        verify(admin).close()
    }

    @Test
    fun `a missing topic is rejected without creating it`() {
        whenever(admin.describeTopics(any<Collection<String>>())).thenThrow(UnknownTopicOrPartitionException("missing"))
        assertThatThrownBy { service().read() }
            .isInstanceOf(InstanceReceivedOffsetException::class.java)
            .hasCauseInstanceOf(UnknownTopicOrPartitionException::class.java)
        verify(admin).close()
    }

    @Test
    fun `missing partition bounds are rejected`() {
        whenever(admin.listOffsets(any<Map<TopicPartition, OffsetSpec>>(), any<ListOffsetsOptions>()))
            .thenReturn(ListOffsetsResult(emptyMap()))
        assertThatThrownBy { service().read() }.isInstanceOf(InstanceReceivedOffsetException::class.java)
        verify(admin).close()
    }

    @Test
    fun `unknown topic identity is rejected`() {
        describe(Uuid.ZERO_UUID)
        assertThatThrownBy { service().read() }.isInstanceOf(InstanceReceivedOffsetException::class.java)
        verify(admin).close()
    }

    @Test
    fun `missing cluster identity is rejected`() {
        whenever(cluster.clusterId()).thenReturn(KafkaFuture.completedFuture(" "))
        assertThatThrownBy { service().read() }.isInstanceOf(InstanceReceivedOffsetException::class.java)
        verify(admin).close()
    }

    private fun service(isolation: String = "read_uncommitted"): InstanceReceivedKafkaMetadataService =
        InstanceReceivedKafkaMetadataService(
            { admin },
            names,
            InstanceReceivedKafkaProperties(Duration.ofMillis(50)),
            receivedConsumerConfig(isolation),
        )

    private fun describe(id: Uuid) {
        val result: DescribeTopicsResult = mock()
        whenever(admin.describeTopics(any<Collection<String>>())).thenReturn(result)
        whenever(result.allTopicNames()).thenReturn(
            KafkaFuture.completedFuture(
                mapOf(
                    RECEIVED_TOPIC to
                        TopicDescription(
                            RECEIVED_TOPIC,
                            false,
                            listOf(TopicPartitionInfo(0, Node(1, "localhost", 9092), emptyList(), emptyList())),
                            emptySet(),
                            id,
                        ),
                ),
            ),
        )
    }
}
