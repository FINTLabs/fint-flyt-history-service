package no.novari.flyt.instance

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
    private val clock: Clock,
    @param:Value("\${fint.application-id}")
    private val applicationId: String,
) {
    @Transactional
    fun register(
        instanceFlowHeaders: InstanceFlowHeaders,
        instance: InstanceObjectDto,
    ): InstanceObjectDto {
        val savedInstance = instanceService.save(instance)
        val instanceId = checkNotNull(savedInstance.id) { "Persisted instance must have an id" }
        val registeredHeaders = instanceFlowHeaders.toBuilder().instanceId(instanceId).build()
        instanceInfoEventService.registerEvent(
            instanceFlowHeaders = registeredHeaders,
            eventCategory = EventCategory.INSTANCE_REGISTERED,
            timestamp = Instant.now(clock),
            applicationId = applicationId,
        )
        return savedInstance
    }
}
