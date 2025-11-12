package no.novari.flyt.history;

import no.novari.flyt.history.exceptions.LatestStatusEventNotOfTypeErrorException;
import no.novari.flyt.history.exceptions.NoPreviousStatusEventsFoundException;
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders;
import no.novari.flyt.history.model.SourceApplicationAggregateInstanceId;
import no.novari.flyt.history.model.action.InstanceStatusTransferredOverrideAction;
import no.novari.flyt.history.model.action.ManuallyProcessedEventAction;
import no.novari.flyt.history.model.action.ManuallyRejectedEventAction;
import no.novari.flyt.history.model.event.Event;
import no.novari.flyt.history.model.event.EventCategory;
import no.novari.flyt.history.model.event.EventType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class ManualEventCreationService {

    private final Clock clock;
    private final UuidService uuidService;
    private final String applicationId;
    private final EventService eventService;

    public ManualEventCreationService(
            Clock clock,
            UuidService uuidService,
            @Value("${fint.application-id}") String applicationId,
            EventService eventService
    ) {
        this.clock = clock;
        this.uuidService = uuidService;
        this.applicationId = applicationId;
        this.eventService = eventService;
    }

    public Event addManuallyProcessedEvent(ManuallyProcessedEventAction manuallyProcessedEventAction) {
        return save(
                manuallyProcessedEventAction,
                EventCategory.INSTANCE_MANUALLY_PROCESSED,
                manuallyProcessedEventAction.getArchiveInstanceId()
        );
    }

    public Event addManuallyRejectedEvent(ManuallyRejectedEventAction manuallyRejectedEventAction) {
        return save(
                manuallyRejectedEventAction,
                EventCategory.INSTANCE_MANUALLY_REJECTED,
                null
        );
    }

    public Event addInstanceStatusOverriddenAsTransferredEvent(
            InstanceStatusTransferredOverrideAction instanceStatusTransferredOverrideAction
    ) {
        return save(
                instanceStatusTransferredOverrideAction,
                EventCategory.INSTANCE_STATUS_OVERRIDDEN_AS_TRANSFERRED,
                null
        );
    }

    public Event save(
            SourceApplicationAggregateInstanceId sourceApplicationAggregateInstanceId,
            EventCategory eventCategory,
            String archiveInstanceId
    ) {
        Optional<Event> latestStatusEventOptional = eventService
                .findLatestStatusEventBySourceApplicationAggregateInstanceId(sourceApplicationAggregateInstanceId);
        if (latestStatusEventOptional.isEmpty()) {
            throw new NoPreviousStatusEventsFoundException();
        }
        Event latestStatusEvent = latestStatusEventOptional.get();
        if (latestStatusEvent.getType() != EventType.ERROR) {
            throw new LatestStatusEventNotOfTypeErrorException();
        }
        return eventService.save(
                Event.builder()
                        .instanceFlowHeaders(
                                InstanceFlowHeaders
                                        .builder()
                                        .sourceApplicationId(sourceApplicationAggregateInstanceId.getSourceApplicationId())
                                        .sourceApplicationIntegrationId(sourceApplicationAggregateInstanceId.getSourceApplicationIntegrationId())
                                        .sourceApplicationInstanceId(sourceApplicationAggregateInstanceId.getSourceApplicationInstanceId())
                                        .integrationId(latestStatusEvent.getInstanceFlowHeaders().getIntegrationId())
                                        .correlationId(uuidService.generateUuid())
                                        .archiveInstanceId(archiveInstanceId)
                                        .build()
                        )
                        .category(eventCategory)
                        .timestamp(OffsetDateTime.now(clock))
                        .type(eventCategory.getType())
                        .applicationId(applicationId)
                        .build()
        );
    }

}
