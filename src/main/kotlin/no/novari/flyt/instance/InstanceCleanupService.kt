package no.novari.flyt.instance

import io.github.oshai.kotlinlogging.KotlinLogging
import no.novari.flyt.history.EventService
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
@ConditionalOnInstanceFunctionality
class InstanceCleanupService(
    private val instanceService: InstanceService,
    private val eventService: EventService,
    private val deletionService: InstanceDeletionService,
    @Value($$"${novari.flyt.history-service.retention.time-to-keep-in-days}")
    private val timeToKeepInDays: Int,
) {
    private val logger = KotlinLogging.logger {}

    @Scheduled(initialDelayString = "30s", fixedDelayString = "24h")
    fun cleanUp() {
        for (instance in instanceService.getAllOlderThan(timeToKeepInDays)) {
            if (Thread.currentThread().isInterrupted) return
            try {
                val instanceId = checkNotNull(instance.id)
                val headers = eventService.findInstanceFlowHeadersForLatestInstanceRegisteredEvent(instanceId)
                if (headers != null) {
                    deletionService.delete(headers)
                } else {
                    instanceService.deleteById(instanceId)
                    logger.warn {
                        "Deleted instance $instanceId without an event because registration headers are missing"
                    }
                }
            } catch (failure: Exception) {
                if (failure is InterruptedException || Thread.currentThread().isInterrupted) {
                    Thread.currentThread().interrupt()
                    return
                }
                logger.error(failure) { "Could not clean up instance ${instance.id}" }
            }
        }
    }
}
