package no.novari.flyt.instance

import io.github.oshai.kotlinlogging.KotlinLogging
import no.novari.flyt.history.InstanceErrorEventService
import no.novari.flyt.history.InstanceInfoEventService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.instance.outbox.InstanceRegisteredOutboxRepository
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.model.Error
import no.novari.flyt.kafka.model.ErrorCollection
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
@ConditionalOnInstanceFunctionality
class InstanceRegistrationService(
    private val instanceService: InstanceService,
    private val instanceInfoEventService: InstanceInfoEventService,
    private val instanceErrorEventService: InstanceErrorEventService,
    private val instanceReceivedOffsetRepository: InstanceReceivedOffsetRepository,
    private val outboxRepository: InstanceRegisteredOutboxRepository,
    private val clock: Clock,
    @param:Value($$"${fint.application-id}")
    private val applicationId: String,
) {
    private val logger = KotlinLogging.logger {}

    @Transactional
    fun register(
        messageId: InstanceReceivedMessageId,
        expectedNextOffset: Long,
        instanceFlowHeaders: InstanceFlowHeaders,
        instance: InstanceObjectDto,
    ): InstanceRegistrationOutcome {
        val nextOffset =
            lockForProcessing(messageId, expectedNextOffset)
                ?: return InstanceRegistrationOutcome.AlreadyProcessed
        InstanceInputValidator.validateHeaders(instanceFlowHeaders)
        InstanceInputValidator.validate(instance)
        val savedInstance = instanceService.save(instance)
        val instanceId = checkNotNull(savedInstance.id) { "Persisted instance must have an id" }
        val registeredHeaders = instanceFlowHeaders.toBuilder().instanceId(instanceId).build()
        val registrationTimestamp = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS)
        instanceInfoEventService.registerEvent(
            instanceFlowHeaders = registeredHeaders,
            eventCategory = EventCategory.INSTANCE_REGISTERED,
            timestamp = registrationTimestamp,
            applicationId = applicationId,
        )
        outboxRepository.enqueue(messageId, registeredHeaders, savedInstance, registrationTimestamp, applicationId)
        instanceReceivedOffsetRepository.advance(messageId, nextOffset)
        return InstanceRegistrationOutcome.Registered(savedInstance)
    }

    @Transactional
    fun reject(
        messageId: InstanceReceivedMessageId,
        expectedNextOffset: Long,
        instanceFlowHeaders: InstanceFlowHeaders,
        failure: InstanceInputException,
    ): InstanceRegistrationOutcome {
        val nextOffset =
            lockForProcessing(messageId, expectedNextOffset)
                ?: return InstanceRegistrationOutcome.AlreadyProcessed
        InstanceInputValidator.validateHeaders(instanceFlowHeaders)
        instanceErrorEventService.registerError(
            instanceFlowHeaders = instanceFlowHeaders,
            eventCategory = EventCategory.INSTANCE_REGISTRATION_ERROR,
            errorCollection = ErrorCollection(listOf(Error(ErrorCode.GENERAL_SYSTEM_ERROR.getCode(), null))),
            timestamp = Instant.now(clock),
            applicationId = applicationId,
        )
        instanceReceivedOffsetRepository.advance(messageId, nextOffset)
        return InstanceRegistrationOutcome.Rejected(failure.code)
    }

    private fun lockForProcessing(
        messageId: InstanceReceivedMessageId,
        expectedNextOffset: Long,
    ): Long? {
        if (expectedNextOffset < 0) {
            throw InstanceReceivedOffsetException("Expected next offset must not be negative")
        }
        val nextOffset = instanceReceivedOffsetRepository.lockNextOffset(messageId)
        if (messageId.offset < nextOffset) {
            logger.info { "Skipping already processed instance-received record: $messageId, nextOffset=$nextOffset" }
            return null
        }
        if (nextOffset != expectedNextOffset) {
            throw InstanceReceivedOffsetException(
                "Unexpected instance-received position for topic=${messageId.topic}, " +
                    "partition=${messageId.partition}: " +
                    "storedNextOffset=$nextOffset, expectedNextOffset=$expectedNextOffset, recordOffset=${messageId.offset}",
            )
        }
        return nextOffset
    }
}
