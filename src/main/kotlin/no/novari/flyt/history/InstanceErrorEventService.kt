package no.novari.flyt.history

import no.novari.flyt.history.mapping.InstanceFlowHeadersMappingService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.event.EventType
import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.history.repository.entities.ErrorEntity
import no.novari.flyt.history.repository.entities.EventEntity
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.model.Error
import no.novari.flyt.kafka.model.ErrorCollection
import no.novari.flyt.kafka.model.InstanceErrorEvent
import no.novari.flyt.kafka.model.InstanceErrorOrigin
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneOffset

@Service
class InstanceErrorEventService(
    private val eventRepository: EventRepository,
    private val instanceFlowHeadersMappingService: InstanceFlowHeadersMappingService,
) {
    @Transactional(propagation = Propagation.REQUIRED)
    fun registerError(
        instanceFlowHeaders: InstanceFlowHeaders,
        instanceErrorEvent: InstanceErrorEvent,
        timestamp: Instant,
        applicationId: String,
    ) {
        saveError(
            instanceFlowHeaders = instanceFlowHeaders,
            eventCategory = instanceErrorEvent.name.toEventCategory(),
            errorCollection = instanceErrorEvent.errors,
            timestamp = timestamp,
            applicationId = applicationId,
        )
    }

    @Transactional(propagation = Propagation.REQUIRED)
    fun registerError(
        instanceFlowHeaders: InstanceFlowHeaders,
        eventCategory: EventCategory,
        errorCollection: ErrorCollection,
        timestamp: Instant,
        applicationId: String,
    ) {
        require(eventCategory.type == EventType.ERROR) { "Event category must have type ERROR" }
        saveError(instanceFlowHeaders, eventCategory, errorCollection, timestamp, applicationId)
    }

    private fun saveError(
        instanceFlowHeaders: InstanceFlowHeaders,
        eventCategory: EventCategory,
        errorCollection: ErrorCollection,
        timestamp: Instant,
        applicationId: String,
    ) {
        eventRepository.save(
            EventEntity(
                instanceFlowHeaders = instanceFlowHeadersMappingService.toEmbeddable(instanceFlowHeaders),
                name = eventCategory.eventName,
                type = EventType.ERROR,
                timestamp = timestamp.atOffset(ZoneOffset.UTC),
                applicationId = applicationId,
                errors = mapToErrorEntities(errorCollection),
            ),
        )
    }

    private fun InstanceErrorOrigin.toEventCategory(): EventCategory =
        when (this) {
            InstanceErrorOrigin.RECEIVAL -> EventCategory.INSTANCE_RECEIVAL_ERROR
            InstanceErrorOrigin.REGISTRATION -> EventCategory.INSTANCE_REGISTRATION_ERROR
            InstanceErrorOrigin.RETRY_REQUEST -> EventCategory.INSTANCE_RETRY_REQUEST_ERROR
            InstanceErrorOrigin.MAPPING -> EventCategory.INSTANCE_MAPPING_ERROR
            InstanceErrorOrigin.DISPATCHING -> EventCategory.INSTANCE_DISPATCHING_ERROR
        }

    private fun mapToErrorEntities(errorCollection: ErrorCollection): MutableCollection<ErrorEntity> =
        errorCollection.errors
            ?.map(::mapToErrorEntity)
            ?.toMutableList()
            ?: mutableListOf()

    private fun mapToErrorEntity(errorFromEvent: Error): ErrorEntity =
        ErrorEntity(
            errorCode = errorFromEvent.errorCode,
            args = errorFromEvent.args?.mapValues { (_, value) -> value.orEmpty() },
        )
}
