package no.novari.flyt.instance

import io.github.oshai.kotlinlogging.KotlinLogging
import no.novari.flyt.history.InstanceInfoEventService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
@ConditionalOnInstanceFunctionality
class InstanceRegistrationService(
    private val instanceService: InstanceService,
    private val instanceInfoEventService: InstanceInfoEventService,
    private val instanceReceivedOffsetRepository: InstanceReceivedOffsetRepository,
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
        if (expectedNextOffset < 0) {
            throw InstanceReceivedOffsetException("Expected next offset must not be negative")
        }
        val nextOffset = instanceReceivedOffsetRepository.lockNextOffset(messageId)
        if (messageId.offset < nextOffset) {
            logger.info {
                "Skipping already processed instance-received record: clusterId=${messageId.clusterId}, " +
                    "topicId=${messageId.topicId}, topic=${messageId.topic}, partition=${messageId.partition}, " +
                    "offset=${messageId.offset}, nextOffset=$nextOffset"
            }
            return InstanceRegistrationOutcome.AlreadyProcessed
        }
        if (nextOffset != expectedNextOffset) {
            throw InstanceReceivedOffsetException(
                "Unexpected instance-received position for topic=${messageId.topic}, " +
                    "partition=${messageId.partition}: " +
                    "storedNextOffset=$nextOffset, expectedNextOffset=$expectedNextOffset, recordOffset=${messageId.offset}",
            )
        }
        val savedInstance = instanceService.save(instance)
        val instanceId = checkNotNull(savedInstance.id) { "Persisted instance must have an id" }
        val registeredHeaders = instanceFlowHeaders.toBuilder().instanceId(instanceId).build()
        instanceInfoEventService.registerEvent(
            instanceFlowHeaders = registeredHeaders,
            eventCategory = EventCategory.INSTANCE_REGISTERED,
            timestamp = Instant.now(clock),
            applicationId = applicationId,
        )
        instanceReceivedOffsetRepository.advance(messageId, nextOffset)
        return InstanceRegistrationOutcome.Registered(savedInstance)
    }
}
