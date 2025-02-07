package no.fintlabs.mapping;

import no.fintlabs.model.event.EventCategorizationService;
import no.fintlabs.model.event.EventCategory;
import no.fintlabs.model.instance.InstanceFlowSummariesFilter;
import no.fintlabs.model.instance.InstanceStorageStatus;
import no.fintlabs.repository.filters.InstanceFlowSummariesQueryFilter;
import no.fintlabs.repository.filters.InstanceStorageStatusQueryFilter;
import no.fintlabs.repository.filters.TimeQueryFilter;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class InstanceStatusFilterMappingService {

    private final EventCategorizationService eventCategorizationService;
    private final TimeFilterMappingService timeFilterMappingService;

    public InstanceStatusFilterMappingService(
            EventCategorizationService eventCategorizationService,
            TimeFilterMappingService timeFilterMappingService
    ) {
        this.eventCategorizationService = eventCategorizationService;
        this.timeFilterMappingService = timeFilterMappingService;
    }

    public InstanceFlowSummariesQueryFilter toQueryFilter(
            InstanceFlowSummariesFilter instanceFlowSummariesFilter
    ) {
        return InstanceFlowSummariesQueryFilter
                .builder()
                .sourceApplicationIds(instanceFlowSummariesFilter.getSourceApplicationIds())
                .sourceApplicationIntegrationIds(instanceFlowSummariesFilter.getSourceApplicationIntegrationIds())
                .sourceApplicationInstanceIds(instanceFlowSummariesFilter.getSourceApplicationInstanceIds())
                .integrationIds(instanceFlowSummariesFilter.getIntegrationIds())
                .timeQueryFilter(Optional.ofNullable(instanceFlowSummariesFilter.getTime())
                        .map(timeFilterMappingService::toQueryFilter)
                        .orElse(TimeQueryFilter.EMPTY)
                )
                .statusEventNames(Optional.ofNullable(instanceFlowSummariesFilter.getStatuses())
                        .map(eventCategorizationService::getEventNamesByInstanceStatuses)
                        .orElse(null))
                .storageStatusFilter(Optional.ofNullable(instanceFlowSummariesFilter.getStorageStatuses())
                        .map(storageStatus ->
                                new InstanceStorageStatusQueryFilter(
                                        eventCategorizationService.getEventNamesByInstanceStorageStatuses(storageStatus),
                                        storageStatus.contains(InstanceStorageStatus.NEVER_STORED)
                                )
                        ).orElse(null))
                .associatedEventNames(
                        Optional.ofNullable(instanceFlowSummariesFilter.getAssociatedEvents())
                                .map(eventCategories -> eventCategories.stream()
                                        .map(EventCategory::getEventName)
                                        .toList()
                                )
                                .orElse(null)
                )
                .destinationIds(instanceFlowSummariesFilter.getDestinationIds())
                .build();
    }

}
