package no.novari.flyt.instance

import io.github.oshai.kotlinlogging.KotlinLogging
import no.novari.flyt.history.InstanceErrorEventService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.flyt.instance.kafka.InstanceRegisteredEventProducerService
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.model.Error
import no.novari.flyt.kafka.model.ErrorCollection
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant

@Service
@ConditionalOnInstanceFunctionality
class InstanceRegistrationService(
    private val instanceService: InstanceService,
    private val instanceErrorEventService: InstanceErrorEventService,
    private val instanceRegisteredEventProducerService: InstanceRegisteredEventProducerService,
    private val instanceReceivedOffsetRepository: InstanceReceivedOffsetRepository,
    private val clock: Clock,
    @param:Value($$"${fint.application-id}")
    private val applicationId: String,
    transactionManager: PlatformTransactionManager,
) {
    private val logger = KotlinLogging.logger {}
    private val transaction = TransactionTemplate(transactionManager)

    /**
     * The position is written last, after the instance is saved and instance-registered is acknowledged,
     * so it never gets ahead of processing. Any failure is retried by the error handler.
     */
    fun register(
        topic: String,
        partition: Int,
        offset: Long,
        instanceFlowHeaders: InstanceFlowHeaders,
        instance: InstanceObjectDto,
    ) {
        // Without a row the start position is unknown: stop before anything is saved or published.
        checkNotNull(instanceReceivedOffsetRepository.findNextOffset(topic, partition)) {
            "No instance_received_offset row for topic=$topic, partition=$partition"
        }
        InstanceInputValidator.validateHeaders(instanceFlowHeaders)
        try {
            InstanceInputValidator.validate(instance)
        } catch (failure: InstanceInputException) {
            logger.warn {
                "Instance-received record rejected: topic=$topic, partition=$partition, offset=$offset, code=${failure.code}"
            }
            transaction.executeWithoutResult {
                instanceErrorEventService.registerError(
                    instanceFlowHeaders = instanceFlowHeaders,
                    eventCategory = EventCategory.INSTANCE_REGISTRATION_ERROR,
                    errorCollection = ErrorCollection(listOf(Error(ErrorCode.GENERAL_SYSTEM_ERROR.getCode(), null))),
                    timestamp = Instant.now(clock),
                    applicationId = applicationId,
                )
                instanceReceivedOffsetRepository.markProcessed(topic, partition, offset)
            }
            return
        }
        val savedInstance = instanceService.save(instance)
        val instanceId = checkNotNull(savedInstance.id) { "Persisted instance must have an id" }
        instanceRegisteredEventProducerService.publish(
            instanceFlowHeaders.toBuilder().instanceId(instanceId).build(),
            savedInstance,
        )
        instanceReceivedOffsetRepository.markProcessed(topic, partition, offset)
    }
}
