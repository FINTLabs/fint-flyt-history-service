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
    fun retry(instanceId: Long) {
        val instance = instanceService.getById(instanceId)
        val headers =
            eventService
                .findInstanceFlowHeadersForLatestInstanceRegisteredEvent(instanceId)
                ?.toBuilder()
                ?.correlationId(UUID.randomUUID())
                ?.build()
                ?: throw MissingRetryHeadersException(instanceId)
        try {
            producer.publish(headers, instance)
        } catch (failure: Exception) {
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

class MissingRetryHeadersException(
    instanceId: Long,
) : RuntimeException("Could not find instance flow headers for registered instance with id=$instanceId")
