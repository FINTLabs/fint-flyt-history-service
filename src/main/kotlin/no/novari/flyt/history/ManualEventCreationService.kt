package no.novari.flyt.history

import no.novari.flyt.history.exceptions.LatestStatusEventNotOfTypeErrorException
import no.novari.flyt.history.exceptions.NoPreviousStatusEventsFoundException
import no.novari.flyt.history.model.SourceApplicationAggregateInstanceId
import no.novari.flyt.history.model.action.InstanceStatusTransferredOverrideAction
import no.novari.flyt.history.model.action.ManuallyProcessedEventAction
import no.novari.flyt.history.model.action.ManuallyRejectedEventAction
import no.novari.flyt.history.model.event.Event
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.event.EventType
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.OffsetDateTime

@Service
class ManualEventCreationService(
    private val clock: Clock,
    private val uuidService: UuidService,
    @param:Value("\${fint.application-id}") private val applicationId: String,
    private val eventService: EventService,
) {
    fun addManuallyProcessedEvent(manuallyProcessedEventAction: ManuallyProcessedEventAction): Event {
        return save(
            sourceApplicationAggregateInstanceId = manuallyProcessedEventAction,
            eventCategory = EventCategory.INSTANCE_MANUALLY_PROCESSED,
            archiveInstanceId = manuallyProcessedEventAction.archiveInstanceId,
        )
    }

    fun addManuallyRejectedEvent(manuallyRejectedEventAction: ManuallyRejectedEventAction): Event {
        return save(
            sourceApplicationAggregateInstanceId = manuallyRejectedEventAction,
            eventCategory = EventCategory.INSTANCE_MANUALLY_REJECTED,
            archiveInstanceId = null,
        )
    }

    fun addInstanceStatusOverriddenAsTransferredEvent(
        instanceStatusTransferredOverrideAction: InstanceStatusTransferredOverrideAction,
    ): Event {
        return save(
            sourceApplicationAggregateInstanceId = instanceStatusTransferredOverrideAction,
            eventCategory = EventCategory.INSTANCE_STATUS_OVERRIDDEN_AS_TRANSFERRED,
            archiveInstanceId = null,
        )
    }

    fun save(
        sourceApplicationAggregateInstanceId: SourceApplicationAggregateInstanceId,
        eventCategory: EventCategory,
        archiveInstanceId: String?,
    ): Event {
        val latestStatusEvent =
            eventService
                .findLatestStatusEventBySourceApplicationAggregateInstanceId(
                    sourceApplicationAggregateInstanceId,
                ) ?: throw NoPreviousStatusEventsFoundException()

        if (latestStatusEvent.type != EventType.ERROR) {
            throw LatestStatusEventNotOfTypeErrorException()
        }

        val latestInstanceFlowHeaders = requireNotNull(latestStatusEvent.instanceFlowHeaders)

        return eventService.save(
            Event(
                instanceFlowHeaders =
                    InstanceFlowHeaders
                        .builder()
                        .sourceApplicationId(requireNotNull(sourceApplicationAggregateInstanceId.sourceApplicationId))
                        .sourceApplicationIntegrationId(
                            requireNotNull(sourceApplicationAggregateInstanceId.sourceApplicationIntegrationId),
                        ).sourceApplicationInstanceId(
                            requireNotNull(sourceApplicationAggregateInstanceId.sourceApplicationInstanceId),
                        ).integrationId(latestInstanceFlowHeaders.integrationId)
                        .correlationId(uuidService.generateUuid())
                        .archiveInstanceId(archiveInstanceId)
                        .build(),
                category = eventCategory,
                timestamp = OffsetDateTime.now(clock),
                type = eventCategory.type,
                applicationId = applicationId,
            ),
        )
    }
}
