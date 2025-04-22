package no.fintlabs.repository.projections;

import java.time.Instant;

public interface InstanceFlowSummaryNativeProjection {
    Long getSourceApplicationId();

    String getSourceApplicationIntegrationId();

    String getSourceApplicationInstanceId();

    Long getIntegrationId();

    Long getLatestInstanceId();

    Instant getLatestUpdate();

    String getLatestStatusEventName();

    String getLatestStorageStatusEventName();

    String getLatestDestinationId(); // TODO 26/03/2025 eivindmorch: Rename to destinationInstanceId
}
