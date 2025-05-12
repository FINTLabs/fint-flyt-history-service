package no.fintlabs;

import no.fintlabs.flyt.kafka.headers.InstanceFlowHeaders;
import no.fintlabs.mapping.*;
import no.fintlabs.model.SourceApplicationAggregateInstanceId;
import no.fintlabs.model.event.Event;
import no.fintlabs.model.event.EventCategorizationService;
import no.fintlabs.model.event.EventCategory;
import no.fintlabs.model.instance.InstanceFlowSummariesFilter;
import no.fintlabs.model.instance.InstanceFlowSummary;
import no.fintlabs.model.statistics.IntegrationStatisticsFilter;
import no.fintlabs.repository.EventRepository;
import no.fintlabs.repository.entities.EventEntity;
import no.fintlabs.repository.entities.InstanceFlowHeadersEmbeddable;
import no.fintlabs.repository.filters.EventNamesPerInstanceStatus;
import no.fintlabs.repository.filters.InstanceFlowSummariesQueryFilter;
import no.fintlabs.repository.filters.IntegrationStatisticsQueryFilter;
import no.fintlabs.repository.projections.InstanceFlowSummaryProjection;
import no.fintlabs.repository.projections.InstanceStatisticsProjection;
import no.fintlabs.repository.projections.IntegrationStatisticsProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EventServiceTest {

    private EventRepository eventRepository;
    private EventMappingService eventMappingService;
    private InstanceFlowHeadersMappingService instanceFlowHeadersMappingService;
    private InstanceFlowSummariesFilterMappingService instanceFlowSummariesFilterMappingService;
    private InstanceFlowSummaryMappingService instanceFlowSummaryMappingService;
    private IntegrationStatisticsFilterMappingService integrationStatisticsFilterMappingService;
    private EventCategorizationService eventCategorizationService;
    private EventService eventService;

    @BeforeEach
    public void setup() {
        eventRepository = mock(EventRepository.class);
        eventMappingService = mock(EventMappingService.class);
        instanceFlowHeadersMappingService = mock(InstanceFlowHeadersMappingService.class);
        instanceFlowSummariesFilterMappingService = mock(InstanceFlowSummariesFilterMappingService.class);
        instanceFlowSummaryMappingService = mock(InstanceFlowSummaryMappingService.class);
        integrationStatisticsFilterMappingService = mock(IntegrationStatisticsFilterMappingService.class);
        eventCategorizationService = mock(EventCategorizationService.class);
        eventService = new EventService(
                eventRepository,
                eventMappingService,
                instanceFlowHeadersMappingService,
                instanceFlowSummariesFilterMappingService,
                instanceFlowSummaryMappingService,
                integrationStatisticsFilterMappingService,
                eventCategorizationService
        );
    }

    @Test
    public void whenSave_thenInvokeDtoToEntityMappingAndEventRepositorySaveAndEntityToDtoMapping() {
        Event event = mock(Event.class);
        EventEntity eventEntity = mock(EventEntity.class);
        EventEntity persistedEventEntity = mock(EventEntity.class);
        Event persistedEvent = mock(Event.class);
        when(eventMappingService.toEventEntity(event))
                .thenReturn(eventEntity);
        when(eventRepository.save(eventEntity))
                .thenReturn(persistedEventEntity);
        when(eventMappingService.toEvent(persistedEventEntity))
                .thenReturn(persistedEvent);

        Event result = eventService.save(event);

        verify(eventMappingService, times(1)).toEventEntity(event);
        verify(eventMappingService, times(1)).toEvent(persistedEventEntity);
        verifyNoMoreInteractions(eventMappingService);

        verify(eventRepository, times(1)).save(eventEntity);
        verifyNoMoreInteractions(eventRepository);

        assertThat(result).isEqualTo(persistedEvent);
    }

    @Test
    void whenGetInstanceFlowSummariesTotalCountInvokeFilterMappingAndGetEventCategorizationAndInvokeRepositoryQuery() {
        InstanceFlowSummariesFilter instanceFlowSummariesFilter = mock(InstanceFlowSummariesFilter.class);
        InstanceFlowSummariesQueryFilter instanceFlowSummariesQueryFilter = mock(InstanceFlowSummariesQueryFilter.class);
        when(instanceFlowSummariesFilterMappingService.toQueryFilter(instanceFlowSummariesFilter))
                .thenReturn(instanceFlowSummariesQueryFilter);

        when(eventCategorizationService.getAllInstanceStatusEventNames()).thenReturn(Set.of("testEventName1"));
        when(eventCategorizationService.getAllInstanceStorageStatusEventNames()).thenReturn(Set.of("testEventName2"));

        when(eventRepository.getInstanceFlowSummariesTotalCount(
                instanceFlowSummariesQueryFilter,
                Set.of("testEventName1"),
                Set.of("testEventName2")
        )).thenReturn(5L);

        long instanceFlowSummariesTotalCount = eventService.getInstanceFlowSummariesTotalCount(
                instanceFlowSummariesFilter
        );
        verify(instanceFlowSummariesFilterMappingService, times(1))
                .toQueryFilter(instanceFlowSummariesFilter);
        verify(eventRepository, times(1)).getInstanceFlowSummariesTotalCount(
                instanceFlowSummariesQueryFilter,
                Set.of("testEventName1"),
                Set.of("testEventName2")
        );
        verify(eventCategorizationService, times(1)).getAllInstanceStatusEventNames();
        verify(eventCategorizationService, times(1)).getAllInstanceStorageStatusEventNames();

        verifyNoMoreInteractions(
                eventRepository,
                eventMappingService,
                instanceFlowHeadersMappingService,
                instanceFlowSummariesFilterMappingService,
                instanceFlowSummaryMappingService,
                integrationStatisticsFilterMappingService,
                eventCategorizationService
        );

        assertThat(instanceFlowSummariesTotalCount).isEqualTo(5L);
    }

    @Test
    void whenGetInstanceFlowSummariesInvokeFilterMappingAndGetEventCategorizationAndInvokeRepositoryQueryAndInstanceFlowMapping() {
        InstanceFlowSummariesFilter instanceFlowSummariesFilter = mock(InstanceFlowSummariesFilter.class);
        InstanceFlowSummariesQueryFilter instanceFlowSummariesQueryFilter = mock(InstanceFlowSummariesQueryFilter.class);
        when(instanceFlowSummariesFilterMappingService.toQueryFilter(instanceFlowSummariesFilter))
                .thenReturn(instanceFlowSummariesQueryFilter);

        when(eventCategorizationService.getAllInstanceStatusEventNames()).thenReturn(Set.of("testEventName1"));
        when(eventCategorizationService.getAllInstanceStorageStatusEventNames()).thenReturn(Set.of("testEventName2"));

        InstanceFlowSummaryProjection instanceFlowSummaryProjection1 = mock(InstanceFlowSummaryProjection.class);
        InstanceFlowSummaryProjection instanceFlowSummaryProjection2 = mock(InstanceFlowSummaryProjection.class);
        when(eventRepository.getInstanceFlowSummaries(
                instanceFlowSummariesQueryFilter,
                Set.of("testEventName1"),
                Set.of("testEventName2"),
                10
        )).thenReturn(List.of(instanceFlowSummaryProjection1, instanceFlowSummaryProjection2));

        InstanceFlowSummary instanceFlowSummary1 = mock(InstanceFlowSummary.class);
        InstanceFlowSummary instanceFlowSummary2 = mock(InstanceFlowSummary.class);
        when(instanceFlowSummaryMappingService.toInstanceFlowSummary(instanceFlowSummaryProjection1))
                .thenReturn(instanceFlowSummary1);
        when(instanceFlowSummaryMappingService.toInstanceFlowSummary(instanceFlowSummaryProjection2))
                .thenReturn(instanceFlowSummary2);

        List<InstanceFlowSummary> instanceFlowSummaries = eventService.getInstanceFlowSummaries(
                instanceFlowSummariesFilter,
                10
        );
        verify(instanceFlowSummariesFilterMappingService, times(1))
                .toQueryFilter(instanceFlowSummariesFilter);
        verify(eventRepository, times(1)).getInstanceFlowSummaries(
                instanceFlowSummariesQueryFilter,
                Set.of("testEventName1"),
                Set.of("testEventName2"),
                10
        );
        verify(instanceFlowSummaryMappingService, times(1))
                .toInstanceFlowSummary(instanceFlowSummaryProjection1);
        verify(instanceFlowSummaryMappingService, times(1))
                .toInstanceFlowSummary(instanceFlowSummaryProjection2);
        verify(eventCategorizationService, times(1)).getAllInstanceStatusEventNames();
        verify(eventCategorizationService, times(1)).getAllInstanceStorageStatusEventNames();

        verifyNoMoreInteractions(
                eventRepository,
                eventMappingService,
                instanceFlowHeadersMappingService,
                instanceFlowSummariesFilterMappingService,
                instanceFlowSummaryMappingService,
                integrationStatisticsFilterMappingService,
                eventCategorizationService
        );

        assertThat(instanceFlowSummaries).isEqualTo(List.of(instanceFlowSummary1, instanceFlowSummary2));
    }

    @Test
    void whenGetAllEventsBySourceApplicationAggregateInstanceIdInvokeRepositoryQueryAndEventMapping() {
        Pageable pageable = mock(Pageable.class);
        Page<EventEntity> eventEntityPage = mock(Page.class);
        Page<Event> eventPage = mock(Page.class);
        when(eventRepository.findAllByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationIntegrationIdAndInstanceFlowHeadersSourceApplicationInstanceId(
                1L,
                "testSourceApplicationIntegrationId",
                "testSourceApplicationInstanceId",
                pageable
        )).thenReturn(eventEntityPage);
        when(eventMappingService.toEventPage(eventEntityPage))
                .thenReturn(eventPage);

        Page<Event> allEventsBySourceApplicationAggregateInstanceId =
                eventService.getAllEventsBySourceApplicationAggregateInstanceId(
                        1L,
                        "testSourceApplicationIntegrationId",
                        "testSourceApplicationInstanceId",
                        pageable
                );

        verify(eventRepository, times(1))
                .findAllByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationIntegrationIdAndInstanceFlowHeadersSourceApplicationInstanceId(
                        1L,
                        "testSourceApplicationIntegrationId",
                        "testSourceApplicationInstanceId",
                        pageable
                );
        verify(eventMappingService, times(1)).toEventPage(eventEntityPage);

        verifyNoMoreInteractions(
                eventRepository,
                eventMappingService,
                instanceFlowHeadersMappingService,
                instanceFlowSummariesFilterMappingService,
                instanceFlowSummaryMappingService,
                integrationStatisticsFilterMappingService,
                eventCategorizationService
        );

        assertThat(allEventsBySourceApplicationAggregateInstanceId).isSameAs(eventPage);
    }

    @Test
    void givenEmptyQueryResult_whenFindInstanceFlowHeadersForLatestInstanceRegisteredEvent_thenReturnEmpty() {
        when(eventRepository.findFirstByInstanceFlowHeadersInstanceIdAndNameOrderByTimestampDesc(
                1L,
                EventCategory.INSTANCE_REGISTERED.getEventName()
        )).thenReturn(Optional.empty());

        Optional<InstanceFlowHeaders> instanceFlowHeadersForLatestInstanceRegisteredEvent =
                eventService.findInstanceFlowHeadersForLatestInstanceRegisteredEvent(1L);

        verify(eventRepository, times(1))
                .findFirstByInstanceFlowHeadersInstanceIdAndNameOrderByTimestampDesc(
                        1L,
                        EventCategory.INSTANCE_REGISTERED.getEventName()
                );

        verifyNoMoreInteractions(
                eventRepository,
                eventMappingService,
                instanceFlowHeadersMappingService,
                instanceFlowSummariesFilterMappingService,
                instanceFlowSummaryMappingService,
                integrationStatisticsFilterMappingService,
                eventCategorizationService
        );

        assertThat(instanceFlowHeadersForLatestInstanceRegisteredEvent).isEmpty();
    }

    @Test
    void givenQueryResultWithValue_whenFindInstanceFlowHeadersForLatestInstanceRegisteredEvent_thenReturnValue() {
        EventEntity eventEntity = mock(EventEntity.class);
        InstanceFlowHeadersEmbeddable instanceFlowHeadersEmbeddable = mock(InstanceFlowHeadersEmbeddable.class);
        InstanceFlowHeaders instanceFlowHeaders = mock(InstanceFlowHeaders.class);
        when(eventEntity.getInstanceFlowHeaders()).thenReturn(instanceFlowHeadersEmbeddable);

        when(eventRepository.findFirstByInstanceFlowHeadersInstanceIdAndNameOrderByTimestampDesc(
                1L,
                EventCategory.INSTANCE_REGISTERED.getEventName()
        )).thenReturn(Optional.of(eventEntity));

        when(instanceFlowHeadersMappingService.toInstanceFlowHeaders(instanceFlowHeadersEmbeddable))
                .thenReturn(instanceFlowHeaders);

        Optional<InstanceFlowHeaders> instanceFlowHeadersForLatestInstanceRegisteredEvent =
                eventService.findInstanceFlowHeadersForLatestInstanceRegisteredEvent(1L);

        verify(eventRepository, times(1))
                .findFirstByInstanceFlowHeadersInstanceIdAndNameOrderByTimestampDesc(
                        1L,
                        EventCategory.INSTANCE_REGISTERED.getEventName()
                );

        verify(instanceFlowHeadersMappingService, times(1))
                .toInstanceFlowHeaders(instanceFlowHeadersEmbeddable);

        verifyNoMoreInteractions(
                eventRepository,
                eventMappingService,
                instanceFlowHeadersMappingService,
                instanceFlowSummariesFilterMappingService,
                instanceFlowSummaryMappingService,
                integrationStatisticsFilterMappingService,
                eventCategorizationService
        );

        assertThat(instanceFlowHeadersForLatestInstanceRegisteredEvent).isNotEmpty();
        assertThat(instanceFlowHeadersForLatestInstanceRegisteredEvent.get()).isSameAs(instanceFlowHeaders);
    }

    @Test
    void givenEmptyQueryResult_whenFindLatestArchiveInstanceId_thenReturnEmpty() {
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

        EventNamesPerInstanceStatus eventNamesPerInstanceStatus = mock(EventNamesPerInstanceStatus.class);
        when(eventCategorizationService.getEventNamesPerInstanceStatus()).thenReturn(eventNamesPerInstanceStatus);

        when(eventRepository.findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
                1L,
                "testSourceApplicationIntegrationId",
                "testSourceApplicationInstanceId"
        )).thenReturn(List.of());

        Optional<String> latestArchiveInstanceId =
                eventService.findLatestArchiveInstanceId(sourceApplicationAggregateInstanceId);

        verify(eventRepository, times(1))
                .findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
                        1L,
                        "testSourceApplicationIntegrationId",
                        "testSourceApplicationInstanceId"
                );

        verifyNoMoreInteractions(
                eventRepository,
                eventMappingService,
                instanceFlowHeadersMappingService,
                instanceFlowSummariesFilterMappingService,
                instanceFlowSummaryMappingService,
                integrationStatisticsFilterMappingService,
                eventCategorizationService
        );

        assertThat(latestArchiveInstanceId).isEmpty();
    }

    @Test
    void givenQueryResultWithValue_whenFindLatestArchiveInstanceId_thenReturnValue() {
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

        EventNamesPerInstanceStatus eventNamesPerInstanceStatus = mock(EventNamesPerInstanceStatus.class);
        when(eventCategorizationService.getEventNamesPerInstanceStatus()).thenReturn(eventNamesPerInstanceStatus);

        when(eventRepository.findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
                1L,
                "testSourceApplicationIntegrationId",
                "testSourceApplicationInstanceId"
        )).thenReturn(List.of("testArchiveInstanceId"));

        Optional<String> latestArchiveInstanceId =
                eventService.findLatestArchiveInstanceId(sourceApplicationAggregateInstanceId);

        verify(eventRepository, times(1))
                .findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
                        1L,
                        "testSourceApplicationIntegrationId",
                        "testSourceApplicationInstanceId"
                );

        verifyNoMoreInteractions(
                eventRepository,
                eventMappingService,
                instanceFlowHeadersMappingService,
                instanceFlowSummariesFilterMappingService,
                instanceFlowSummaryMappingService,
                integrationStatisticsFilterMappingService,
                eventCategorizationService
        );

        assertThat(latestArchiveInstanceId).isNotEmpty();
        assertThat(latestArchiveInstanceId.get()).isEqualTo("testArchiveInstanceId");
    }

    @Test
    void givenQueryResultWithMultipleValues_whenFindLatestArchiveInstanceId_thenReturnFirstValue() {
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

        EventNamesPerInstanceStatus eventNamesPerInstanceStatus = mock(EventNamesPerInstanceStatus.class);
        when(eventCategorizationService.getEventNamesPerInstanceStatus()).thenReturn(eventNamesPerInstanceStatus);

        when(eventRepository.findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
                1L,
                "testSourceApplicationIntegrationId",
                "testSourceApplicationInstanceId"
        )).thenReturn(List.of("testArchiveInstanceId1", "testArchiveInstanceId2", "testArchiveInstanceId3"));

        Optional<String> latestArchiveInstanceId =
                eventService.findLatestArchiveInstanceId(sourceApplicationAggregateInstanceId);

        verify(eventRepository, times(1))
                .findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
                        1L,
                        "testSourceApplicationIntegrationId",
                        "testSourceApplicationInstanceId"
                );

        verifyNoMoreInteractions(
                eventRepository,
                eventMappingService,
                instanceFlowHeadersMappingService,
                instanceFlowSummariesFilterMappingService,
                instanceFlowSummaryMappingService,
                integrationStatisticsFilterMappingService,
                eventCategorizationService
        );

        assertThat(latestArchiveInstanceId).isNotEmpty();
        assertThat(latestArchiveInstanceId.get()).isEqualTo("testArchiveInstanceId1");
    }

    @Test
    void givenEmptyQueryResult_whenFindLatestStatusEventBySourceApplicationAggregateInstanceId_thenReturnEmpty() {
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

        when(eventCategorizationService.getAllInstanceStatusEventNames()).thenReturn(Set.of("statusEventName1"));

        when(eventRepository.findLatestStatusEventBySourceApplicationAggregateInstanceId(
                sourceApplicationAggregateInstanceId,
                Set.of("statusEventName1")
        )).thenReturn(Optional.empty());

        Optional<Event> latestStatusEventBySourceApplicationAggregateInstanceId =
                eventService.findLatestStatusEventBySourceApplicationAggregateInstanceId(
                        sourceApplicationAggregateInstanceId
                );

        verify(eventCategorizationService, times(1)).getAllInstanceStatusEventNames();

        verify(eventRepository, times(1))
                .findLatestStatusEventBySourceApplicationAggregateInstanceId(
                        sourceApplicationAggregateInstanceId,
                        Set.of("statusEventName1")
                );

        verifyNoMoreInteractions(
                eventRepository,
                eventMappingService,
                instanceFlowHeadersMappingService,
                instanceFlowSummariesFilterMappingService,
                instanceFlowSummaryMappingService,
                integrationStatisticsFilterMappingService,
                eventCategorizationService
        );

        assertThat(latestStatusEventBySourceApplicationAggregateInstanceId).isEmpty();
    }

    @Test
    void givenQueryResultWithEvent_whenFindLatestStatusEventBySourceApplicationAggregateInstanceId_thenReturnEvent() {
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

        when(eventCategorizationService.getAllInstanceStatusEventNames()).thenReturn(Set.of("statusEventName1"));

        EventEntity eventEntity = mock(EventEntity.class);
        when(eventRepository.findLatestStatusEventBySourceApplicationAggregateInstanceId(
                sourceApplicationAggregateInstanceId,
                Set.of("statusEventName1")
        )).thenReturn(Optional.of(eventEntity));

        Event event = mock(Event.class);
        when(eventMappingService.toEvent(eventEntity)).thenReturn(event);

        Optional<Event> latestStatusEventBySourceApplicationAggregateInstanceId =
                eventService.findLatestStatusEventBySourceApplicationAggregateInstanceId(
                        sourceApplicationAggregateInstanceId
                );

        verify(eventCategorizationService, times(1)).getAllInstanceStatusEventNames();

        verify(eventRepository, times(1))
                .findLatestStatusEventBySourceApplicationAggregateInstanceId(
                        sourceApplicationAggregateInstanceId,
                        Set.of("statusEventName1")
                );

        verify(eventMappingService, times(1)).toEvent(eventEntity);

        verifyNoMoreInteractions(
                eventRepository,
                eventMappingService,
                instanceFlowHeadersMappingService,
                instanceFlowSummariesFilterMappingService,
                instanceFlowSummaryMappingService,
                integrationStatisticsFilterMappingService,
                eventCategorizationService
        );

        assertThat(latestStatusEventBySourceApplicationAggregateInstanceId).isNotEmpty();
        assertThat(latestStatusEventBySourceApplicationAggregateInstanceId.get()).isEqualTo(event);
    }

    // TODO 08/04/2025 eivindmorch: Test names
    @Test
    void whenGetStatistics_thenInvoke() {
        List<Long> sourceApplicationIds = mock(List.class);
        EventNamesPerInstanceStatus eventNamesPerInstanceStatus = mock(EventNamesPerInstanceStatus.class);
        when(eventCategorizationService.getEventNamesPerInstanceStatus()).thenReturn(eventNamesPerInstanceStatus);

        InstanceStatisticsProjection instanceStatisticsProjection = mock(InstanceStatisticsProjection.class);
        when(eventRepository.getTotalStatistics(sourceApplicationIds, eventNamesPerInstanceStatus))
                .thenReturn(instanceStatisticsProjection);

        InstanceStatisticsProjection statistics = eventService.getStatistics(sourceApplicationIds);

        verify(eventCategorizationService, times(1)).getEventNamesPerInstanceStatus();

        verify(eventRepository, times(1))
                .getTotalStatistics(sourceApplicationIds, eventNamesPerInstanceStatus);

        verifyNoMoreInteractions(
                eventRepository,
                eventMappingService,
                instanceFlowHeadersMappingService,
                instanceFlowSummariesFilterMappingService,
                instanceFlowSummaryMappingService,
                integrationStatisticsFilterMappingService,
                eventCategorizationService
        );

        assertThat(statistics).isSameAs(instanceStatisticsProjection);
    }

    @Test
    void getIntegrationStatistics() {
        IntegrationStatisticsFilter integrationStatisticsFilter = mock(IntegrationStatisticsFilter.class);
        Pageable pageable = mock(Pageable.class);
        IntegrationStatisticsQueryFilter integrationStatisticsQueryFilter = mock(IntegrationStatisticsQueryFilter.class);

        when(integrationStatisticsFilterMappingService.toQueryFilter(integrationStatisticsFilter))
                .thenReturn(integrationStatisticsQueryFilter);

        EventNamesPerInstanceStatus eventNamesPerInstanceStatus = mock(EventNamesPerInstanceStatus.class);
        when(eventCategorizationService.getEventNamesPerInstanceStatus()).thenReturn(eventNamesPerInstanceStatus);

        Slice<IntegrationStatisticsProjection> slice = mock(Slice.class);
        when(eventRepository.getIntegrationStatistics(
                integrationStatisticsQueryFilter,
                eventNamesPerInstanceStatus,
                pageable
        )).thenReturn(slice);

        Slice<IntegrationStatisticsProjection> integrationStatistics = eventService.getIntegrationStatistics(
                integrationStatisticsFilter,
                pageable
        );

        verify(integrationStatisticsFilterMappingService, times(1))
                .toQueryFilter(integrationStatisticsFilter);

        verify(eventCategorizationService, times(1)).getEventNamesPerInstanceStatus();

        verify(eventRepository, times(1)).getIntegrationStatistics(
                integrationStatisticsQueryFilter,
                eventNamesPerInstanceStatus,
                pageable
        );

        verifyNoMoreInteractions(
                eventRepository,
                eventMappingService,
                instanceFlowHeadersMappingService,
                instanceFlowSummariesFilterMappingService,
                instanceFlowSummaryMappingService,
                integrationStatisticsFilterMappingService,
                eventCategorizationService
        );

        assertThat(integrationStatistics).isNotNull();
    }
}