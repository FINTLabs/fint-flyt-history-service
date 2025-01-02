package no.fintlabs.mapping;

import no.fintlabs.model.event.EventCategorizationService;
import no.fintlabs.model.instance.InstanceInfoFilter;
import no.fintlabs.model.instance.InstanceStorageStatus;
import no.fintlabs.repository.filters.InstanceInfoQueryFilter;
import no.fintlabs.repository.filters.InstanceStorageStatusQueryFilter;
import org.springframework.stereotype.Service;

@Service
public class InstanceStatusFilterMappingService {

    private final EventCategorizationService eventCategorizationService;

    public InstanceStatusFilterMappingService(EventCategorizationService eventCategorizationService) {
        this.eventCategorizationService = eventCategorizationService;
    }

    public InstanceInfoQueryFilter toQueryFilter(
            InstanceInfoFilter instanceInfoFilter
    ) {
        return InstanceInfoQueryFilter
                .builder()
                .sourceApplicationIds(instanceInfoFilter.getSourceApplicationIds().orElse(null))
                .sourceApplicationIntegrationIds(instanceInfoFilter.getSourceApplicationIntegrationIds().orElse(null))
                .sourceApplicationInstanceIds(instanceInfoFilter.getSourceApplicationInstanceIds().orElse(null))
                .integrationIds(instanceInfoFilter.getIntegrationIds().orElse(null))
                .latestStatusTimestampMin(instanceInfoFilter.getLatestStatusTimestampMin().orElse(null))
                .latestStatusTimestampMax(instanceInfoFilter.getLatestStatusTimestampMax().orElse(null))
                .statusEventNames(instanceInfoFilter.getStatuses()
                        .map(eventCategorizationService::getEventNamesByInstanceStatuses)
                        .orElse(null))
                .storageStatusFilter(instanceInfoFilter.getStorageStatuses()
                        .map(storageStatus ->
                                new InstanceStorageStatusQueryFilter(
                                        eventCategorizationService.getEventNamesByInstanceStorageStatuses(storageStatus),
                                        storageStatus.contains(InstanceStorageStatus.NEVER_STORED)
                                )
                        ).orElse(null))
                .associatedEventNames(instanceInfoFilter.getAssociatedEventNames().orElse(null))
                .destinationIds(instanceInfoFilter.getDestinationIds().orElse(null))
                .build();
    }
}
