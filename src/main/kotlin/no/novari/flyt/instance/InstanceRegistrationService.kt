package no.novari.flyt.instance

import io.github.oshai.kotlinlogging.KotlinLogging
import no.novari.flyt.history.InstanceErrorEventService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.flyt.instance.kafka.InstanceRegisteredEventProducerService
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.CouldNotReadInstanceFlowHeadersException
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeadersMapper
import no.novari.flyt.kafka.instanceflow.headers.NoInstanceFlowHeadersException
import no.novari.flyt.kafka.model.Error
import no.novari.flyt.kafka.model.ErrorCollection
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
@ConditionalOnInstanceFunctionality
class InstanceRegistrationService(
    private val instanceService: InstanceService,
    private val instanceErrorEventService: InstanceErrorEventService,
    private val instanceRegisteredEventProducerService: InstanceRegisteredEventProducerService,
    private val instanceReceivedOffsetRepository: InstanceReceivedOffsetRepository,
    private val positions: InstanceReceivedPositions,
    private val headersMapper: InstanceFlowHeadersMapper,
    private val clock: Clock,
    @param:Value($$"${fint.application-id}")
    private val applicationId: String,
    transactionManager: PlatformTransactionManager,
) {
    private val logger = KotlinLogging.logger {}
    private val transaction = TransactionTemplate(transactionManager)

    // A retry of the same record reuses what earlier attempts completed, so a failed send does not save a new instance.
    private val inProgress = ConcurrentHashMap<TopicPartition, InProgress>()

    /**
     * Registers the record if it is the next one for its partition. The position is written last, after the instance
     * is saved and instance-registered is acknowledged, so it never gets ahead of processing. Technical failures are
     * thrown and retried by the error handler.
     */
    fun register(record: ConsumerRecord<String, InstanceObjectDto?>) {
        if (!positions.isNext(record)) return
        val headers =
            try {
                headersMapper.getInstanceFlowHeaders(record.headers()).also(InstanceInputValidator::validateHeaders)
            } catch (failure: Exception) {
                if (failure !is NoInstanceFlowHeadersException &&
                    failure !is CouldNotReadInstanceFlowHeadersException &&
                    failure !is IllegalArgumentException
                ) {
                    throw failure
                }
                // No usable headers means no history can be stored for the record.
                logger.error {
                    "Instance-received record skipped without history, unusable headers: ${describe(
                        record,
                    )}"
                }
                markProcessed(record)
                return
            }
        val instance = record.value()
        val inputError =
            when {
                instance == null -> {
                    "missing payload"
                }

                else -> {
                    // Validation only reads the payload, so any failure, such as a JSON null where text is expected,
                    // is an input error and not a technical one.
                    runCatching { InstanceInputValidator.validate(instance) }.exceptionOrNull()?.let { failure ->
                        (failure as? InstanceInputException)?.code?.name ?: "invalid payload: $failure"
                    }
                }
            }
        if (inputError != null) {
            reject(record, headers, inputError)
        } else {
            register(record, headers, checkNotNull(instance))
        }
    }

    private fun register(
        record: ConsumerRecord<String, InstanceObjectDto?>,
        headers: InstanceFlowHeaders,
        instance: InstanceObjectDto,
    ) {
        val partition = TopicPartition(record.topic(), record.partition())
        val progress =
            inProgress[partition]?.takeIf { it.offset == record.offset() }
                ?: InProgress(record.offset(), instanceService.save(instance)).also { inProgress[partition] = it }
        if (!progress.published) {
            val instanceId = checkNotNull(progress.instance.id) { "Persisted instance must have an id" }
            instanceRegisteredEventProducerService.publish(
                headers.toBuilder().instanceId(instanceId).build(),
                progress.instance,
            )
            progress.published = true
        }
        markProcessed(record)
        inProgress.remove(partition)
    }

    private fun reject(
        record: ConsumerRecord<String, InstanceObjectDto?>,
        headers: InstanceFlowHeaders,
        reason: String,
    ) {
        logger.warn { "Instance-received record rejected: ${describe(record)}, reason=$reason" }
        transaction.executeWithoutResult {
            instanceErrorEventService.registerError(
                instanceFlowHeaders = headers,
                eventCategory = EventCategory.INSTANCE_REGISTRATION_ERROR,
                errorCollection = ErrorCollection(listOf(Error(ErrorCode.GENERAL_SYSTEM_ERROR.getCode(), null))),
                timestamp = Instant.now(clock),
                applicationId = applicationId,
            )
            instanceReceivedOffsetRepository.markProcessed(record.topic(), record.partition(), record.offset())
        }
        positions.advanced(record)
    }

    private fun markProcessed(record: ConsumerRecord<*, *>) {
        instanceReceivedOffsetRepository.markProcessed(record.topic(), record.partition(), record.offset())
        positions.advanced(record)
    }

    private fun describe(record: ConsumerRecord<*, *>) =
        "topic=${record.topic()}, partition=${record.partition()}, offset=${record.offset()}"

    private class InProgress(
        val offset: Long,
        val instance: InstanceObjectDto,
        var published: Boolean = false,
    )
}
