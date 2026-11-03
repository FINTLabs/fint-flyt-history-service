package no.novari.flyt.instance

import no.novari.flyt.history.EventService
import no.novari.flyt.history.InstanceErrorEventService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.flyt.instance.kafka.InstanceRequestedForRetryEventProducerService
import no.novari.flyt.kafka.model.Error
import no.novari.flyt.kafka.model.ErrorCollection
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
@ConditionalOnInstanceFunctionality
class InstanceRetryService(
    private val instanceService: InstanceService,
    private val eventService: EventService,
    private val producer: InstanceRequestedForRetryEventProducerService,
    private val instanceErrorEventService: InstanceErrorEventService,
    private val clock: Clock,
    @param:Value($$"${fint.application-id}")
    private val applicationId: String,
) {
    @Transactional(propagation = Propagation.NEVER)
    fun retry(instanceId: Long) {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Instance retry interrupted")
        val instance = instanceService.getById(instanceId)
        val headers =
            eventService
                .findInstanceFlowHeadersForLatestInstanceRegisteredEvent(instanceId)
                ?.toBuilder()
                ?.correlationId(UUID.randomUUID())
                ?.build()
                ?: throw NoInstanceFlowHeadersException(instanceId)
        try {
            producer.publish(headers, instance)
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw failure
        } catch (failure: Exception) {
            if (Thread.currentThread().isInterrupted) throw failure
            try {
                instanceErrorEventService.registerError(
                    headers,
                    EventCategory.INSTANCE_RETRY_REQUEST_ERROR,
                    ErrorCollection(listOf(Error(ErrorCode.GENERAL_SYSTEM_ERROR.getCode(), null))),
                    Instant.now(clock),
                    applicationId,
                )
            } catch (historyFailure: Exception) {
                failure.addSuppressed(historyFailure)
            }
            throw failure
        }
    }
}

class NoInstanceFlowHeadersException(
    instanceId: Long,
) : RuntimeException("Could not find instance flow headers for registered instance with id=$instanceId")
