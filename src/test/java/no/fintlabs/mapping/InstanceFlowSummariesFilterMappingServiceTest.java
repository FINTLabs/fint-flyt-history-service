package no.fintlabs.mapping;

import no.fintlabs.model.event.EventCategorizationService;
import no.fintlabs.model.event.EventCategory;
import no.fintlabs.model.instance.InstanceFlowSummariesFilter;
import no.fintlabs.model.instance.InstanceStatus;
import no.fintlabs.model.instance.InstanceStorageStatus;
import no.fintlabs.model.time.TimeFilter;
import no.fintlabs.repository.filters.InstanceFlowSummariesQueryFilter;
import no.fintlabs.repository.filters.InstanceStorageStatusQueryFilter;
import no.fintlabs.repository.filters.TimeQueryFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class InstanceFlowSummariesFilterMappingServiceTest {

    private EventCategorizationService eventCategorizationService;
    private TimeFilterMappingService timeFilterMappingService;
    private InstanceFlowSummariesFilterMappingService instanceFlowSummariesFilterMappingService;


    @BeforeEach
    public void setup() {
        eventCategorizationService = mock(EventCategorizationService.class);
        timeFilterMappingService = mock(TimeFilterMappingService.class);
        instanceFlowSummariesFilterMappingService = new InstanceFlowSummariesFilterMappingService(
                eventCategorizationService,
                timeFilterMappingService
        );
    }

    @Test
    public void givenNullInstanceFlowSummariesFilter_whenToQueryFilter_thenThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> instanceFlowSummariesFilterMappingService.toQueryFilter(null)
        );
        verifyNoMoreInteractions(eventCategorizationService, timeFilterMappingService);
    }

    @Test
    public void givenEmptyInstanceFlowSummariesFilter_whenToQueryFilter_thenReturnEmptyInstanceFlowSummariesQueryFilter() {
        InstanceFlowSummariesQueryFilter queryFilter = instanceFlowSummariesFilterMappingService.toQueryFilter(
                InstanceFlowSummariesFilter.builder().build()
        );
        verifyNoMoreInteractions(eventCategorizationService, timeFilterMappingService);
        assertThat(queryFilter).isEqualTo(InstanceFlowSummariesQueryFilter.builder().build());
    }

    @Test
    public void givenInstanceFlowSummariesFilterWithValues_whenToQueryFilter_thenReturnInstanceFlowSummariesQueryFilterWithValues() {

        when(eventCategorizationService.getEventNamesByInstanceStatuses(
                List.of(
                        InstanceStatus.IN_PROGRESS,
                        InstanceStatus.ABORTED
                )
        )).thenReturn(Set.of(
                "testStatusName1",
                "testStatusName2"
        ));

        when(eventCategorizationService.getEventNamesByInstanceStorageStatuses(
                List.of(
                        InstanceStorageStatus.STORED,
                        InstanceStorageStatus.NEVER_STORED
                )
        )).thenReturn(Set.of(
                "testStorageStatusName1"
        ));

        TimeFilter timeFilter = mock(TimeFilter.class);
        TimeQueryFilter timeQueryFilter = mock(TimeQueryFilter.class);
        when(timeFilterMappingService.toQueryFilter(timeFilter)).thenReturn(timeQueryFilter);

        InstanceFlowSummariesQueryFilter queryFilter = instanceFlowSummariesFilterMappingService.toQueryFilter(
                InstanceFlowSummariesFilter
                        .builder()
                        .time(timeFilter)
                        .sourceApplicationIds(List.of(1L, 2L))
                        .sourceApplicationIntegrationIds(List.of(
                                "testSourceApplicationIntegrationId1",
                                "testSourceApplicationIntegrationId2"
                        ))
                        .sourceApplicationInstanceIds(List.of(
                                "testSourceApplicationInstanceId1",
                                "testSourceApplicationInstanceId2"
                        ))
                        .integrationIds(List.of(10L, 11L))
                        .statuses(List.of(
                                InstanceStatus.IN_PROGRESS,
                                InstanceStatus.ABORTED
                        ))
                        .storageStatuses(List.of(
                                InstanceStorageStatus.STORED,
                                InstanceStorageStatus.NEVER_STORED
                        ))
                        .associatedEvents(List.of(
                                EventCategory.INSTANCE_REGISTERED,
                                EventCategory.INSTANCE_RECEIVAL_ERROR
                        ))
                        .destinationIds(List.of(
                                "testDestinationId1",
                                "testDestinationId2"
                        ))
                        .build()
        );
        verify(eventCategorizationService, times(1)).getEventNamesByInstanceStatuses(
                List.of(
                        InstanceStatus.IN_PROGRESS,
                        InstanceStatus.ABORTED
                )
        );
        verify(eventCategorizationService, times(1)).getEventNamesByInstanceStorageStatuses(
                List.of(
                        InstanceStorageStatus.STORED,
                        InstanceStorageStatus.NEVER_STORED
                )
        );
        verify(timeFilterMappingService, times(1)).toQueryFilter(timeFilter);

        verifyNoMoreInteractions(eventCategorizationService, timeFilterMappingService);

        assertThat(queryFilter).isEqualTo(
                InstanceFlowSummariesQueryFilter
                        .builder()
                        .sourceApplicationIds(List.of(1L, 2L))
                        .sourceApplicationIntegrationIds(List.of(
                                "testSourceApplicationIntegrationId1",
                                "testSourceApplicationIntegrationId2"
                        ))
                        .sourceApplicationInstanceIds(List.of(
                                "testSourceApplicationInstanceId1",
                                "testSourceApplicationInstanceId2"
                        ))
                        .integrationIds(List.of(10L, 11L))
                        .statusEventNames(Set.of(
                                "testStatusName1",
                                "testStatusName2"
                        ))
                        .instanceStorageStatusQueryFilter(
                                new InstanceStorageStatusQueryFilter(
                                        Set.of("testStorageStatusName1"),
                                        true
                                )
                        )
                        .associatedEventNames(List.of(
                                EventCategory.INSTANCE_REGISTERED.getEventName(),
                                EventCategory.INSTANCE_RECEIVAL_ERROR.getEventName()
                        ))
                        .destinationIds(List.of(
                                "testDestinationId1",
                                "testDestinationId2"
                        ))
                        .timeQueryFilter(timeQueryFilter)
                        .build()
        );
    }

}
