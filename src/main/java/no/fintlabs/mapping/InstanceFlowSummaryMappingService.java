package no.fintlabs.mapping;

import no.fintlabs.model.event.EventCategorizationService;
import no.fintlabs.model.event.EventCategory;
import no.fintlabs.model.instance.InstanceFlowSummary;
import no.fintlabs.model.instance.InstanceStorageStatus;
import no.fintlabs.repository.projections.InstanceFlowSummaryProjection;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class InstanceFlowSummaryMappingService {

    private final EventCategorizationService eventCategorizationService;

    public InstanceFlowSummaryMappingService(EventCategorizationService eventCategorizationService) {
        this.eventCategorizationService = eventCategorizationService;
    }

    public InstanceFlowSummary toInstanceFlowSummary(InstanceFlowSummaryProjection projection) {
        return InstanceFlowSummary
                .builder()
                .sourceApplicationId(projection.getSourceApplicationId())
                .sourceApplicationIntegrationId(projection.getSourceApplicationIntegrationId())
                .sourceApplicationInstanceId(projection.getSourceApplicationInstanceId())
                .integrationId(projection.getIntegrationId())
                .latestInstanceId(projection.getLatestInstanceId())
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
                .latestDestinationId(projection.getLatestDestinationId())
                .build();
    }
}
