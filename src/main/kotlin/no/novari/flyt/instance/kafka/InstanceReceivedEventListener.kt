package no.novari.flyt.instance.kafka

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import no.novari.flyt.instance.InstanceReceivedMessageId
import no.novari.flyt.instance.InstanceReceivedOffsetException
import no.novari.flyt.instance.InstanceReceivedOffsetRepository
import no.novari.flyt.instance.InstanceReceivedPosition
import no.novari.flyt.instance.InstanceReceivedPositionService
import no.novari.flyt.instance.InstanceRegistrationHandler
import no.novari.flyt.instance.InstanceRegistrationOutcome
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeadersMapper
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.springframework.dao.RecoverableDataAccessException
import org.springframework.dao.TransientDataAccessException
import org.springframework.jdbc.CannotGetJdbcConnectionException
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener
import org.springframework.kafka.listener.MessageListener
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
@ConditionalOnInstanceFunctionality
class InstanceReceivedEventListener(
    private val positions: InstanceReceivedPositionService,
    private val offsets: InstanceReceivedOffsetRepository,
    private val registration: InstanceRegistrationHandler,
    private val headersMapper: InstanceFlowHeadersMapper,
    objectMapper: ObjectMapper,
) : MessageListener<String, ByteArray>,
    ConsumerAwareRebalanceListener {
    private val assigned = ConcurrentHashMap<TopicPartition, InstanceReceivedPosition>()
    private val payloadReader =
        objectMapper
            .readerFor(InstanceObjectDto::class.java)
            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)

    override fun onPartitionsAssigned(
        consumer: Consumer<*, *>,
        partitions: Collection<TopicPartition>,
    ) {
        val validated = positions.getValidatedPositions(partitions.map { it.partition() }.toSet())
        val assignment =
            partitions.associateWith { partition ->
                val position = validated.getValue(partition.partition())
                if (position.topic != partition.topic()) {
                    throw InstanceReceivedOffsetException(
                        "Assigned topic differs from the validated instance-received topic",
                    )
                }
                position
            }
        assignment.forEach { (partition, position) -> consumer.seek(partition, position.nextOffset) }
        assigned.putAll(assignment)
    }

    override fun onPartitionsRevokedBeforeCommit(
        consumer: Consumer<*, *>,
        partitions: Collection<TopicPartition>,
    ) {
        partitions.forEach(assigned::remove)
    }

    override fun onPartitionsLost(
        consumer: Consumer<*, *>,
        partitions: Collection<TopicPartition>,
    ) {
        partitions.forEach(assigned::remove)
    }

    override fun onMessage(record: ConsumerRecord<String, ByteArray>) {
        try {
            val partition = TopicPartition(record.topic(), record.partition())
            val position =
                assigned[partition]
                    ?: throw InstanceReceivedOffsetException("No validated assignment for $partition")
            val messageId =
                InstanceReceivedMessageId(
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    checkNotNull(position.clusterId),
                    checkNotNull(position.topicId),
                )
            val instance = decodePayload(record)
            val headers =
                try {
                    headersMapper.getInstanceFlowHeaders(record.headers())
                } catch (failure: Exception) {
                    throw InstanceReceivedProcessingException("Unable to decode instance flow headers", failure, false)
                }
            val outcome = registration.handle(messageId, position.nextOffset, headers, instance)
            assigned[partition] =
                when (outcome) {
                    is InstanceRegistrationOutcome.Registered, is InstanceRegistrationOutcome.Rejected -> {
                        position.copy(nextOffset = Math.incrementExact(record.offset()))
                    }

                    InstanceRegistrationOutcome.AlreadyProcessed -> {
                        currentPosition(position)
                    }
                }
        } catch (failure: InstanceReceivedOffsetException) {
            throw failure
        } catch (failure: InstanceReceivedProcessingException) {
            throw failure
        } catch (failure: Exception) {
            val retryable =
                generateSequence(failure as Throwable) { it.cause }.any {
                    it is TransientDataAccessException || it is RecoverableDataAccessException ||
                        it is CannotGetJdbcConnectionException
                }
            throw InstanceReceivedProcessingException("Instance-received processing failed", failure, retryable)
        }
    }

    private fun decodePayload(record: ConsumerRecord<String, ByteArray>): InstanceObjectDto =
        try {
            requireNotNull(record.value()) { "Missing instance payload" }
            requireNotNull(payloadReader.readValue<InstanceObjectDto>(record.value())) { "Null instance payload" }
        } catch (failure: Exception) {
            throw InstanceReceivedProcessingException("Unable to decode instance payload", failure, false)
        }

    private fun currentPosition(previous: InstanceReceivedPosition): InstanceReceivedPosition {
        val current =
            offsets.find(previous.topic, previous.partition)
                ?: throw InstanceReceivedOffsetException(
                    "Instance-received position disappeared for ${previous.topic}-${previous.partition}",
                )
        if (current.clusterId != previous.clusterId || current.topicId != previous.topicId ||
            current.nextOffset < previous.nextOffset
        ) {
            throw InstanceReceivedOffsetException(
                "Instance-received identity or progress changed for ${previous.topic}-${previous.partition}",
            )
        }
        return current
    }
}
