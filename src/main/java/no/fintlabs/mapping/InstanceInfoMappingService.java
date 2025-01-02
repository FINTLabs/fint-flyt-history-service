package no.fintlabs.mapping;

import no.fintlabs.model.event.EventCategorizationService;
import no.fintlabs.model.event.EventCategory;
import no.fintlabs.model.instance.InstanceInfo;
import no.fintlabs.model.instance.InstanceStorageStatus;
import no.fintlabs.repository.projections.InstanceInfoProjection;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class InstanceInfoMappingService {

    private final EventCategorizationService eventCategorizationService;

    public InstanceInfoMappingService(EventCategorizationService eventCategorizationService) {
        this.eventCategorizationService = eventCategorizationService;
    }

    public InstanceInfo toInstanceInfo(InstanceInfoProjection projection) {
        return InstanceInfo
                .builder()
                .sourceApplicationId(projection.getSourceApplicationId())
                .sourceApplicationIntegrationId(projection.getSourceApplicationIntegrationId())
                .sourceApplicationInstanceId(projection.getSourceApplicationInstanceId())
                .integrationId(projection.getIntegrationId())
                .latestUpdate(projection.getLatestUpdate())
                .status(
                        eventCategorizationService.getCategoryByName(projection.getLatestStatusEventName())
                                .getInstanceStatus()
                )
                .intermediateStorageStatus(
                        Optional.ofNullable(projection.getLatestStorageStatusEventName())
                                .map(eventCategorizationService::getCategoryByName)
                                .map(EventCategory::getInstanceStorageStatus)
                                .orElse(InstanceStorageStatus.NEVER_STORED)
                )
                .destinationId(projection.getDestinationId())
                .build();
    }
}
