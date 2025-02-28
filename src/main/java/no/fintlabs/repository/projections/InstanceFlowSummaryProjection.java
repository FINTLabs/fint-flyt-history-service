package no.fintlabs.repository.projections;

import java.time.OffsetDateTime;

public interface InstanceFlowSummaryProjection {
    Long getSourceApplicationId();

    String getSourceApplicationIntegrationId();

    String getSourceApplicationInstanceId();

    Long getIntegrationId();

    OffsetDateTime getLatestUpdate();

    String getLatestStatusEventName();

    String getLatestStorageStatusEventName();

    String getDestinationId();
}
