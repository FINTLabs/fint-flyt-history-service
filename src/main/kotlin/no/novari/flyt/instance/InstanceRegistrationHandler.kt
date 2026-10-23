package no.novari.flyt.instance

import io.github.oshai.kotlinlogging.KotlinLogging
import no.novari.flyt.history.InstanceErrorEventService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.model.Error
import no.novari.flyt.kafka.model.ErrorCollection
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
@ConditionalOnInstanceFunctionality
class InstanceRegistrationHandler(
    private val instanceRegistrationService: InstanceRegistrationService,
    private val instanceErrorEventService: InstanceErrorEventService,
    private val clock: Clock,
    @param:Value($$"${fint.application-id}")
    private val applicationId: String,
) {
    private val logger = KotlinLogging.logger {}

    @Transactional(propagation = Propagation.NEVER)
    fun handle(
        messageId: InstanceReceivedMessageId,
        expectedNextOffset: Long,
        instanceFlowHeaders: InstanceFlowHeaders,
        instance: InstanceObjectDto,
    ): InstanceRegistrationOutcome {
        try {
            return instanceRegistrationService.register(messageId, expectedNextOffset, instanceFlowHeaders, instance)
        } catch (offsetFailure: InstanceReceivedOffsetException) {
            logger.error(
                offsetFailure,
            ) { "Instance registration stopped because its persisted Kafka position is invalid" }
            throw offsetFailure
        } catch (registrationFailure: Exception) {
            try {
                instanceErrorEventService.registerError(
                    instanceFlowHeaders = instanceFlowHeaders,
                    eventCategory = EventCategory.INSTANCE_REGISTRATION_ERROR,
                    errorCollection = ErrorCollection(listOf(Error(ErrorCode.GENERAL_SYSTEM_ERROR.getCode(), null))),
                    timestamp = Instant.now(clock),
                    applicationId = applicationId,
                )
            } catch (errorRecordingFailure: Exception) {
                registrationFailure.addSuppressed(errorRecordingFailure)
            }
            throw registrationFailure
        }
    }
}
