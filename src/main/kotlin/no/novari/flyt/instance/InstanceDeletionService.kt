package no.novari.flyt.instance

import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.flyt.instance.kafka.InstanceDeletedEventProducerService
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@ConditionalOnInstanceFunctionality
class InstanceDeletionService(
    private val instanceService: InstanceService,
    private val producer: InstanceDeletedEventProducerService,
) {
    // The delete must commit before publishing, including when called from another service.
    @Transactional(propagation = Propagation.NEVER)
    fun delete(headers: InstanceFlowHeaders) {
        val instanceId = requireNotNull(headers.instanceId) { "Instance flow headers must contain instance id" }
        instanceService.deleteById(instanceId)
        producer.publish(headers)
    }
}
