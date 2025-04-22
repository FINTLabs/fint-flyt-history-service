package no.fintlabs.mapping;

import no.fintlabs.model.event.EventCategorizationService;
import no.fintlabs.model.instance.InstanceFlowSummary;
import no.fintlabs.model.instance.InstanceStorageStatus;
import no.fintlabs.repository.projections.InstanceFlowSummaryProjection;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class InstanceFlowSummaryMappingService {

    private final EventCategorizationService eventCategorizationService;

    public InstanceFlowSummaryMappingService(EventCategorizationService eventCategorizationService) {
        this.eventCategorizationService = eventCategorizationService;
    }

    public InstanceFlowSummary toInstanceFlowSummary(InstanceFlowSummaryProjection projection) {
        if (projection == null) {
            throw new IllegalArgumentException("Projection is null");
        }
        return InstanceFlowSummary
                .builder()
                .sourceApplicationId(projection.getSourceApplicationId())
                .sourceApplicationIntegrationId(projection.getSourceApplicationIntegrationId())
                .sourceApplicationInstanceId(projection.getSourceApplicationInstanceId())
                .integrationId(projection.getIntegrationId())
                .latestInstanceId(projection.getLatestInstanceId())
                .latestUpdate(projection.getLatestUpdate())
                .status(
                        Optional.ofNullable(projection.getLatestStatusEventName())
                                .map(eventCategorizationService::getStatusByEventName)
                                .orElse(null)
                )
                .intermediateStorageStatus(
                        Optional.ofNullable(projection.getLatestStorageStatusEventName())
                                .map(eventCategorizationService::getStorageStatusByEventName)
                                .orElse(InstanceStorageStatus.NEVER_STORED)
                )
                .latestDestinationId(projection.getLatestDestinationId())
                .build();
    }
}
