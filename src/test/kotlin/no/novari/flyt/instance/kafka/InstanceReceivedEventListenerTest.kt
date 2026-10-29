package no.novari.flyt.instance.kafka

import no.novari.flyt.instance.InstanceReceivedMessageId
import no.novari.flyt.instance.InstanceReceivedOffsetException
import no.novari.flyt.instance.InstanceReceivedOffsetRepository
import no.novari.flyt.instance.InstanceReceivedPosition
import no.novari.flyt.instance.InstanceReceivedPositionService
import no.novari.flyt.instance.InstanceRegistrationHandler
import no.novari.flyt.instance.InstanceRegistrationOutcome
import no.novari.flyt.instance.RECEIVED_CLUSTER
import no.novari.flyt.instance.RECEIVED_TOPIC
import no.novari.flyt.instance.RECEIVED_TOPIC_ID
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.instance.outbox.outboxObjectMapper
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeadersMapper
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.CannotGetJdbcConnectionException
import java.util.UUID

class InstanceReceivedEventListenerTest {
    private val positions: InstanceReceivedPositionService = mock()
    private val offsets: InstanceReceivedOffsetRepository = mock()
    private val registration: InstanceRegistrationHandler = mock()
    private val consumer: Consumer<String, ByteArray> = mock()
    private val mapper = outboxObjectMapper()
    private val headersMapper = InstanceFlowHeadersMapper(mapper)
    private val listener = InstanceReceivedEventListener(positions, offsets, registration, headersMapper, mapper)
    private val partition = TopicPartition(RECEIVED_TOPIC, 0)
    private val position = InstanceReceivedPosition(RECEIVED_TOPIC, 0, 5, RECEIVED_CLUSTER, RECEIVED_TOPIC_ID)
    private val headers =
        InstanceFlowHeaders
            .builder()
            .sourceApplicationId(1L)
            .sourceApplicationIntegrationId("integration")
            .sourceApplicationInstanceId("instance")
            .correlationId(UUID.randomUUID())
            .build()
    private val instance = InstanceObjectDto(valuePerKey = mutableMapOf("value" to "secret-payload"))

    @Test
    fun `assignment validates all partitions once before seeking to database positions`() {
        val second = TopicPartition(RECEIVED_TOPIC, 1)
        whenever(positions.getValidatedPositions(setOf(0, 1)))
            .thenReturn(mapOf(0 to position, 1 to position.copy(partition = 1, nextOffset = 9)))

        listener.onPartitionsAssigned(consumer, listOf(partition, second))

        verify(positions).getValidatedPositions(setOf(0, 1))
        verify(consumer).seek(partition, 5)
        verify(consumer).seek(second, 9)
        verifyNoInteractions(registration, offsets)
    }

    @Test
    fun `assignment failure cannot choose a fallback or process records`() {
        val failure = InstanceReceivedOffsetException("No initialized position")
        doThrow(failure).whenever(positions).getValidatedPositions(setOf(0))
        assertThatThrownBy { listener.onPartitionsAssigned(consumer, listOf(partition)) }.isSameAs(failure)
        assertThatThrownBy { listener.onMessage(record(5)) }.isInstanceOf(InstanceReceivedOffsetException::class.java)
        verifyNoInteractions(consumer, registration)
    }

    @Test
    fun `registered and rejected records move expected position across Kafka offset gaps`() {
        assign()
        whenever(registration.handle(any(), any(), any(), any()))
            .thenReturn(InstanceRegistrationOutcome.Registered(instance))
            .thenReturn(
                InstanceRegistrationOutcome.Rejected(no.novari.flyt.instance.InstanceInputErrorCode.KEY_TOO_LONG),
            ).thenReturn(InstanceRegistrationOutcome.Registered(instance))

        listener.onMessage(record(8))
        listener.onMessage(record(9))
        listener.onMessage(record(10))

        val expected = argumentCaptor<Long>()
        val ids = argumentCaptor<InstanceReceivedMessageId>()
        verify(registration, times(3)).handle(ids.capture(), expected.capture(), eq(headers), eq(instance))
        assertThat(expected.allValues).containsExactly(5, 9, 10)
        assertThat(ids.allValues.map { it.offset }).containsExactly(8, 9, 10)
        assertThat(ids.firstValue.clusterId).isEqualTo(RECEIVED_CLUSTER)
        assertThat(ids.firstValue.topicId).isEqualTo(RECEIVED_TOPIC_ID)
        verify(positions).getValidatedPositions(setOf(0))
    }

    @Test
    fun `already processed reloads database progress without another metadata lookup`() {
        assign()
        whenever(registration.handle(any(), any(), any(), any()))
            .thenReturn(InstanceRegistrationOutcome.AlreadyProcessed)
            .thenReturn(InstanceRegistrationOutcome.Registered(instance))
        whenever(offsets.find(RECEIVED_TOPIC, 0)).thenReturn(position.copy(nextOffset = 10))

        listener.onMessage(record(3))
        listener.onMessage(record(10))

        val expected = argumentCaptor<Long>()
        verify(registration, times(2)).handle(any(), expected.capture(), any(), any())
        assertThat(expected.allValues).containsExactly(5, 10)
        verify(positions).getValidatedPositions(setOf(0))
    }

    @ParameterizedTest
    @ValueSource(strings = ["missing", "generation", "rewind"])
    fun `duplicate cannot adopt missing changed or rewound progress`(change: String) {
        assign()
        whenever(
            registration.handle(any(), any(), any(), any()),
        ).thenReturn(InstanceRegistrationOutcome.AlreadyProcessed)
        val current =
            when (change) {
                "missing" -> null
                "generation" -> position.copy(topicId = UUID.randomUUID())
                else -> position.copy(nextOffset = 4)
            }
        whenever(offsets.find(RECEIVED_TOPIC, 0)).thenReturn(current)

        assertThatThrownBy { listener.onMessage(record(3)) }.isInstanceOf(InstanceReceivedOffsetException::class.java)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `revoked or lost assignment cannot process records using stale state`(lost: Boolean) {
        assign()
        if (lost) {
            listener.onPartitionsLost(consumer, listOf(partition))
        } else {
            listener.onPartitionsRevokedBeforeCommit(consumer, listOf(partition))
        }

        assertThatThrownBy { listener.onMessage(record(5)) }.isInstanceOf(InstanceReceivedOffsetException::class.java)
        verifyNoInteractions(registration)
    }

    @ParameterizedTest
    @ValueSource(strings = ["payload", "headers", "missing-headers", "null-payload"])
    fun `unreadable input stops without exposing source content`(invalidPart: String) {
        assign()
        val record = record(5)
        val invalid =
            when (invalidPart) {
                "payload" -> {
                    ConsumerRecord<String, ByteArray>(RECEIVED_TOPIC, 0, 5, null, "secret-invalid-json".toByteArray())
                }

                "headers" -> {
                    record.apply {
                        headers().remove("flyt.instance-flow-headers")
                        headers().add("flyt.instance-flow-headers", "secret-invalid-headers".toByteArray())
                    }
                }

                "missing-headers" -> {
                    record.apply { headers().remove("flyt.instance-flow-headers") }
                }

                else -> {
                    ConsumerRecord<String, ByteArray>(RECEIVED_TOPIC, 0, 5, null, null)
                }
            }
        val failure = assertThrows<InstanceReceivedProcessingException> { listener.onMessage(invalid) }

        assertThat(failure.retryable).isFalse()
        assertThat(failure.message).doesNotContain("secret")
        assertThat(failure.cause).isNull()
        verifyNoInteractions(registration)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `only known infrastructure errors are retryable and exception messages are sanitized`(transient: Boolean) {
        assign()
        val original =
            if (transient) {
                CannotGetJdbcConnectionException("secret-database-details")
            } else {
                DataIntegrityViolationException("secret-row-contents")
            }
        doThrow(original).whenever(registration).handle(any(), any(), any(), any())

        val failure = assertThrows<InstanceReceivedProcessingException> { listener.onMessage(record(5)) }

        assertThat(failure.retryable).isEqualTo(transient)
        assertThat(failure.message).contains(original.javaClass.name).doesNotContain("secret")
        assertThat(failure.cause).isNull()
        assertThat(failure.stackTrace).containsExactly(*original.stackTrace)
    }

    private fun assign() {
        whenever(positions.getValidatedPositions(setOf(0))).thenReturn(mapOf(0 to position))
        listener.onPartitionsAssigned(consumer, listOf(partition))
    }

    private fun record(offset: Long): ConsumerRecord<String, ByteArray> =
        ConsumerRecord<String, ByteArray>(RECEIVED_TOPIC, 0, offset, null, mapper.writeValueAsBytes(instance))
            .apply { headers().add(headersMapper.toHeader(headers)) }
}
