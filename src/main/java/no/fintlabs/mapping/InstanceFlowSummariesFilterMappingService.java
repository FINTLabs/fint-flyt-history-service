package no.fintlabs.mapping;

import no.fintlabs.model.event.EventCategorizationService;
import no.fintlabs.model.event.EventCategory;
import no.fintlabs.model.instance.InstanceFlowSummariesFilter;
import no.fintlabs.model.instance.InstanceStorageStatus;
import no.fintlabs.repository.filters.InstanceFlowSummariesQueryFilter;
import no.fintlabs.repository.filters.InstanceStorageStatusQueryFilter;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class InstanceFlowSummariesFilterMappingService {

    private final EventCategorizationService eventCategorizationService;
    private final TimeFilterMappingService timeFilterMappingService;

    public InstanceFlowSummariesFilterMappingService(
            EventCategorizationService eventCategorizationService,
            TimeFilterMappingService timeFilterMappingService
    ) {
        this.eventCategorizationService = eventCategorizationService;
        this.timeFilterMappingService = timeFilterMappingService;
    }

    public InstanceFlowSummariesQueryFilter toQueryFilter(InstanceFlowSummariesFilter instanceFlowSummariesFilter) {
        if (instanceFlowSummariesFilter == null) {
            throw new IllegalArgumentException("Instance flow summaries filter is null");
        }
        return InstanceFlowSummariesQueryFilter
                .builder()
                .sourceApplicationIds(instanceFlowSummariesFilter.getSourceApplicationIds())
                .sourceApplicationIntegrationIds(instanceFlowSummariesFilter.getSourceApplicationIntegrationIds())
                .sourceApplicationInstanceIds(instanceFlowSummariesFilter.getSourceApplicationInstanceIds())
                .integrationIds(instanceFlowSummariesFilter.getIntegrationIds())
                .statusEventNames(
                        Optional.ofNullable(instanceFlowSummariesFilter.getStatuses())
                                .map(eventCategorizationService::getEventNamesByInstanceStatuses)
                                .orElse(null)
                )
                .instanceStorageStatusQueryFilter(
                        Optional.ofNullable(instanceFlowSummariesFilter.getStorageStatuses())
                                .map(storageStatus ->
                                        new InstanceStorageStatusQueryFilter(
                                                eventCategorizationService.getEventNamesByInstanceStorageStatuses(storageStatus),
                                                storageStatus.contains(InstanceStorageStatus.NEVER_STORED)
                                        )
                                ).orElse(InstanceStorageStatusQueryFilter.EMPTY)
                )
                .associatedEventNames(
                        Optional.ofNullable(instanceFlowSummariesFilter.getAssociatedEvents())
                                .map(eventCategories -> eventCategories.stream()
                                        .map(EventCategory::getEventName)
                                        .toList()
                                ).orElse(null)
                )
                .destinationIds(instanceFlowSummariesFilter.getDestinationIds())
                .timeQueryFilter(timeFilterMappingService.toQueryFilter(instanceFlowSummariesFilter.getTime()))
                .build();
    }
}
