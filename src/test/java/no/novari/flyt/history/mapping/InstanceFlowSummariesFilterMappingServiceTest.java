package no.novari.flyt.history.mapping;

import no.novari.flyt.history.model.event.EventCategorizationService;
import no.novari.flyt.history.model.event.EventCategory;
import no.novari.flyt.history.model.instance.InstanceFlowSummariesFilter;
import no.novari.flyt.history.model.instance.InstanceStatus;
import no.novari.flyt.history.model.instance.InstanceStorageStatus;
import no.novari.flyt.history.model.time.TimeFilter;
import no.novari.flyt.history.repository.filters.InstanceFlowSummariesQueryFilter;
import no.novari.flyt.history.repository.filters.InstanceStorageStatusQueryFilter;
import no.novari.flyt.history.repository.filters.TimeQueryFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class InstanceFlowSummariesFilterMappingServiceTest {

    private Validator validator;
    private EventCategorizationService eventCategorizationService;
    private TimeFilterMappingService timeFilterMappingService;
    private InstanceFlowSummariesFilterMappingService instanceFlowSummariesFilterMappingService;


    @BeforeEach
    public void setup() {
        validator = mock(Validator.class);
        eventCategorizationService = mock(EventCategorizationService.class);
        timeFilterMappingService = mock(TimeFilterMappingService.class);
        instanceFlowSummariesFilterMappingService = new InstanceFlowSummariesFilterMappingService(
                validator,
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
        verifyNoMoreInteractions(validator, eventCategorizationService, timeFilterMappingService);
    }

    @Test
    public void givenValidationErrorOnInstanceFlowSummariesFilter_whenToQueryFilter_thenThrowException() {
        InstanceFlowSummariesFilter instanceFlowSummariesFilter = InstanceFlowSummariesFilter.builder().build();
        when(validator.validate(instanceFlowSummariesFilter)).thenReturn(Set.of(mock(ConstraintViolation.class)));
        assertThrows(
                IllegalArgumentException.class,
                () -> instanceFlowSummariesFilterMappingService.toQueryFilter(instanceFlowSummariesFilter)
        );
        verify(validator, times(1)).validate(instanceFlowSummariesFilter);
        verifyNoMoreInteractions(validator, eventCategorizationService, timeFilterMappingService);
    }

    @Test
    public void givenEmptyInstanceFlowSummariesFilter_whenToQueryFilter_thenReturnEmptyInstanceFlowSummariesQueryFilter() {
        InstanceFlowSummariesFilter instanceFlowSummariesFilter = InstanceFlowSummariesFilter.builder().build();
        when(validator.validate(instanceFlowSummariesFilter)).thenReturn(Set.of());
        InstanceFlowSummariesQueryFilter queryFilter = instanceFlowSummariesFilterMappingService.toQueryFilter(
                instanceFlowSummariesFilter
        );
        verify(validator, times(1)).validate(instanceFlowSummariesFilter);
        verifyNoMoreInteractions(validator, eventCategorizationService, timeFilterMappingService);
        assertThat(queryFilter).isEqualTo(InstanceFlowSummariesQueryFilter.builder().build());
    }

    @Test
    public void givenInstanceFlowSummariesFilterWithValues_whenToQueryFilter_thenReturnInstanceFlowSummariesQueryFilterWithValues() {
        TimeFilter timeFilter = mock(TimeFilter.class);
        TimeQueryFilter timeQueryFilter = mock(TimeQueryFilter.class);

        InstanceFlowSummariesFilter instanceFlowSummariesFilter = InstanceFlowSummariesFilter
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
                .build();

        when(validator.validate(instanceFlowSummariesFilter)).thenReturn(Set.of());
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

        when(timeFilterMappingService.toQueryFilter(timeFilter, ZoneId.of("Europe/Oslo"))).thenReturn(timeQueryFilter);

        InstanceFlowSummariesQueryFilter queryFilter = instanceFlowSummariesFilterMappingService.toQueryFilter(
                instanceFlowSummariesFilter
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
        verify(timeFilterMappingService, times(1)).toQueryFilter(timeFilter, ZoneId.of("Europe/Oslo"));

        verify(validator, times(1)).validate(instanceFlowSummariesFilter);

        verifyNoMoreInteractions(validator, eventCategorizationService, timeFilterMappingService);

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

    @Test
    public void givenInstanceFlowSummariesWithStatuses_whenToQueryFilter_thenReturnInstanceFlowSummariesQueryFilterEventNamesForStatuses() {
        InstanceFlowSummariesFilter instanceFlowSummariesFilter = InstanceFlowSummariesFilter
                .builder()
                .statuses(List.of(
                        InstanceStatus.IN_PROGRESS,
                        InstanceStatus.ABORTED
                ))
                .build();

        when(validator.validate(instanceFlowSummariesFilter)).thenReturn(Set.of());
        when(eventCategorizationService.getEventNamesByInstanceStatuses(
                List.of(
                        InstanceStatus.IN_PROGRESS,
                        InstanceStatus.ABORTED
                )
        )).thenReturn(Set.of(
                "testStatusName1",
                "testStatusName2"
        ));

        InstanceFlowSummariesQueryFilter queryFilter = instanceFlowSummariesFilterMappingService.toQueryFilter(
                instanceFlowSummariesFilter
        );
        verify(eventCategorizationService, times(1)).getEventNamesByInstanceStatuses(
                List.of(
                        InstanceStatus.IN_PROGRESS,
                        InstanceStatus.ABORTED
                )
        );

        verify(validator, times(1)).validate(instanceFlowSummariesFilter);

        verifyNoMoreInteractions(validator, eventCategorizationService, timeFilterMappingService);

        assertThat(queryFilter).isEqualTo(
                InstanceFlowSummariesQueryFilter
                        .builder()
                        .statusEventNames(Set.of(
                                "testStatusName1",
                                "testStatusName2"
                        ))
                        .build()
        );
    }

    @Test
    public void givenInstanceFlowSummariesWithLatestStatusEvents_whenToQueryFilter_thenReturnInstanceFlowSummariesQueryFilterEventNamesForStatuses() {
        InstanceFlowSummariesFilter instanceFlowSummariesFilter = InstanceFlowSummariesFilter
                .builder()
                .latestStatusEvents(List.of(
                        EventCategory.INSTANCE_DISPATCHED,
                        EventCategory.INSTANCE_DISPATCHING_ERROR
                ))
                .build();

        when(validator.validate(instanceFlowSummariesFilter)).thenReturn(Set.of());

        InstanceFlowSummariesQueryFilter queryFilter = instanceFlowSummariesFilterMappingService.toQueryFilter(
                instanceFlowSummariesFilter
        );

        verify(validator, times(1)).validate(instanceFlowSummariesFilter);

        verifyNoMoreInteractions(validator, eventCategorizationService, timeFilterMappingService);

        assertThat(queryFilter).isEqualTo(
                InstanceFlowSummariesQueryFilter
                        .builder()
                        .statusEventNames(Set.of(
                                EventCategory.INSTANCE_DISPATCHED.getEventName(),
                                EventCategory.INSTANCE_DISPATCHING_ERROR.getEventName()
                        ))
                        .build()
        );
    }

}
