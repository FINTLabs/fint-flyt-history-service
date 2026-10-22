package no.novari.flyt.instance

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
    @param:Value("\${fint.application-id}")
    private val applicationId: String,
) {
    @Transactional(propagation = Propagation.NEVER)
    fun handle(
        instanceFlowHeaders: InstanceFlowHeaders,
        instance: InstanceObjectDto,
    ): InstanceObjectDto {
        try {
            return instanceRegistrationService.register(instanceFlowHeaders, instance)
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
