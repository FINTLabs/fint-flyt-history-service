package no.novari.flyt.instance

import io.github.oshai.kotlinlogging.KotlinLogging
import no.novari.flyt.instance.kafka.InstanceDeletedEventProducerService
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.springframework.stereotype.Service

/**
 * Active regardless of the instance functionality flag: until cutover, instance-service deletes dispatched instances
 * too. Whichever service deletes the row publishes the event; the other finds nothing and skips it.
 */
@Service
class InstanceDeletionService(
    private val instanceService: InstanceService,
    private val producer: InstanceDeletedEventProducerService,
) {
    private val logger = KotlinLogging.logger {}

    fun delete(headers: InstanceFlowHeaders) {
        val instanceId = requireNotNull(headers.instanceId) { "Instance flow headers must contain instance id" }
        if (!instanceService.deleteIfExists(instanceId)) {
            logger.info { "Instance $instanceId was already deleted; skipping deletion event" }
            return
        }
        producer.publish(headers)
    }
}
