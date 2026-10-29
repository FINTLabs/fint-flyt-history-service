package no.novari.flyt.instance

import io.github.oshai.kotlinlogging.KotlinLogging
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@ConditionalOnInstanceFunctionality
class InstanceRegistrationHandler(
    private val instanceRegistrationService: InstanceRegistrationService,
) {
    private val logger = KotlinLogging.logger {}

    @Transactional(propagation = Propagation.NEVER)
    fun handle(
        messageId: InstanceReceivedMessageId,
        expectedNextOffset: Long,
        instanceFlowHeaders: InstanceFlowHeaders,
        instance: InstanceObjectDto,
    ): InstanceRegistrationOutcome =
        try {
            val outcome =
                try {
                    instanceRegistrationService.register(messageId, expectedNextOffset, instanceFlowHeaders, instance)
                } catch (failure: InstanceInputException) {
                    instanceRegistrationService.reject(messageId, expectedNextOffset, instanceFlowHeaders, failure)
                }
            if (outcome is InstanceRegistrationOutcome.Rejected) {
                logger.warn { "Instance-received record rejected: $messageId, code=${outcome.code}" }
            }
            outcome
        } catch (failure: InstanceReceivedOffsetException) {
            logger.error(
                failure,
            ) { "Instance registration stopped because its persisted Kafka position is invalid: $messageId" }
            throw failure
        }
}
