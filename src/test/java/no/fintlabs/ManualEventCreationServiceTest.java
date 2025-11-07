package no.fintlabs;

import no.fintlabs.exceptions.LatestStatusEventNotOfTypeErrorException;
import no.fintlabs.exceptions.NoPreviousStatusEventsFoundException;
import no.fintlabs.flyt.kafka.instanceflow.headers.InstanceFlowHeaders;
import no.fintlabs.model.SourceApplicationAggregateInstanceId;
import no.fintlabs.model.event.Event;
import no.fintlabs.model.event.EventCategory;
import no.fintlabs.model.event.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ManualEventCreationServiceTest {

    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(100000), ZoneOffset.UTC);
    private UuidService uuidService;
    private EventService eventService;
    private ManualEventCreationService manualEventCreationService;

    @BeforeEach
    void setup() {
        uuidService = mock(UuidService.class);
        eventService = mock(EventService.class);
        manualEventCreationService = new ManualEventCreationService(
                clock,
                uuidService,
                "testApplicationId",
                eventService
        );
    }

    @Test
    public void givenNoPreviousEventForAggregateSourceApplicationId_whenSave_thenThrowException() {
        SourceApplicationAggregateInstanceId sourceApplicationAggregateInstanceId =
                new SourceApplicationAggregateInstanceId() {
                    @Override
                    public Long getSourceApplicationId() {
                        return 1L;
                    }

                    @Override
                    public String getSourceApplicationIntegrationId() {
                        return "testSourceApplicationIntegrationId";
                    }

                    @Override
                    public String getSourceApplicationInstanceId() {
                        return "testSourceApplicationInstanceId";
                    }
                };

        EventCategory eventCategory = EventCategory.INSTANCE_RECEIVED;

        when(eventService.findLatestStatusEventBySourceApplicationAggregateInstanceId(
                sourceApplicationAggregateInstanceId
        )).thenReturn(Optional.empty());

        assertThrows(
                NoPreviousStatusEventsFoundException.class,
                () -> manualEventCreationService.save(
                        sourceApplicationAggregateInstanceId,
                        eventCategory,
                        "testArchiveInstanceId"
                )
        );

        verify(eventService, times(1))
                .findLatestStatusEventBySourceApplicationAggregateInstanceId(
                        sourceApplicationAggregateInstanceId
                );

        verifyNoMoreInteractions(
                uuidService,
                eventService
        );
    }

    @Test
    public void givenPreviousEventForAggregateSourceApplicationIdNotOfTypeError_whenSave_thenThrowException() {
        SourceApplicationAggregateInstanceId sourceApplicationAggregateInstanceId =
                new SourceApplicationAggregateInstanceId() {
                    @Override
                    public Long getSourceApplicationId() {
                        return 1L;
                    }

                    @Override
                    public String getSourceApplicationIntegrationId() {
                        return "testSourceApplicationIntegrationId";
                    }

                    @Override
                    public String getSourceApplicationInstanceId() {
                        return "testSourceApplicationInstanceId";
                    }
                };

        EventCategory eventCategory = EventCategory.INSTANCE_RECEIVED;

        Event event = mock(Event.class);
        when(event.getType()).thenReturn(EventType.INFO);

        when(eventService.findLatestStatusEventBySourceApplicationAggregateInstanceId(
                sourceApplicationAggregateInstanceId
        )).thenReturn(Optional.of(event));

        assertThrows(
                LatestStatusEventNotOfTypeErrorException.class,
                () -> manualEventCreationService.save(
                        sourceApplicationAggregateInstanceId,
                        eventCategory,
                        "testArchiveInstanceId"
                )
        );

        verify(eventService, times(1))
                .findLatestStatusEventBySourceApplicationAggregateInstanceId(
                        sourceApplicationAggregateInstanceId
                );

        verifyNoMoreInteractions(
                uuidService,
                eventService
        );
    }

    @Test
    public void givenPreviousEventForAggregateSourceApplicationIdIsOfTypeInfo_whenSave_thenCallRepositorySaveAndReturnResult() {
        SourceApplicationAggregateInstanceId sourceApplicationAggregateInstanceId =
                new SourceApplicationAggregateInstanceId() {
                    @Override
                    public Long getSourceApplicationId() {
                        return 1L;
                    }

                    @Override
                    public String getSourceApplicationIntegrationId() {
                        return "testSourceApplicationIntegrationId";
                    }

                    @Override
                    public String getSourceApplicationInstanceId() {
                        return "testSourceApplicationInstanceId";
                    }
                };

        Event latestEvent = mock(Event.class);
        when(latestEvent.getType()).thenReturn(EventType.ERROR);
        InstanceFlowHeaders latestEventInstanceFlowHeaders = mock(InstanceFlowHeaders.class);
        when(latestEventInstanceFlowHeaders.getIntegrationId()).thenReturn(10L);
        when(latestEvent.getInstanceFlowHeaders()).thenReturn(latestEventInstanceFlowHeaders);

        when(eventService.findLatestStatusEventBySourceApplicationAggregateInstanceId(
                sourceApplicationAggregateInstanceId
        )).thenReturn(Optional.of(latestEvent));

        Event newEvent = mock(Event.class);

        UUID uuid = UUID.fromString("079c8300-8af7-4955-87bd-1efce87d52cd");
        when(uuidService.generateUuid()).thenReturn(uuid);

        when(eventService.save(argThat(e ->
                e.getInstanceFlowHeaders().equals(
                        InstanceFlowHeaders
                                .builder()
                                .sourceApplicationId(1L)
                                .sourceApplicationIntegrationId("testSourceApplicationIntegrationId")
                                .sourceApplicationInstanceId("testSourceApplicationInstanceId")
                                .integrationId(10L)
                                .correlationId(uuid)
                                .archiveInstanceId("testArchiveInstanceId")
                                .build()
                ) && e.getCategory().equals(EventCategory.INSTANCE_MANUALLY_PROCESSED)
                && e.getTimestamp().isEqual(OffsetDateTime.now(clock))
                && e.getType().equals(EventType.INFO)
                && e.getApplicationId().equals("testApplicationId")
        ))).thenReturn(newEvent);

        Event result = manualEventCreationService.save(
                sourceApplicationAggregateInstanceId,
                EventCategory.INSTANCE_MANUALLY_PROCESSED,
                "testArchiveInstanceId"
        );

        verify(eventService, times(1))
                .findLatestStatusEventBySourceApplicationAggregateInstanceId(
                        sourceApplicationAggregateInstanceId
                );

        verify(uuidService, times(1)).generateUuid();

        verify(eventService, times(1)).save(argThat(e ->
                e.getInstanceFlowHeaders().equals(
                        InstanceFlowHeaders
                                .builder()
                                .sourceApplicationId(1L)
                                .sourceApplicationIntegrationId("testSourceApplicationIntegrationId")
                                .sourceApplicationInstanceId("testSourceApplicationInstanceId")
                                .integrationId(10L)
                                .correlationId(uuid)
                                .archiveInstanceId("testArchiveInstanceId")
                                .build()
                ) && e.getCategory().equals(EventCategory.INSTANCE_MANUALLY_PROCESSED)
                && e.getTimestamp().isEqual(OffsetDateTime.now(clock))
                && e.getType().equals(EventType.INFO)
                && e.getApplicationId().equals("testApplicationId")
        ));

        verifyNoMoreInteractions(
                uuidService,
                eventService
        );

        assertThat(result).isEqualTo(newEvent);
    }

}