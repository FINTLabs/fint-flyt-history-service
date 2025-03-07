package no.fintlabs.repository.projections;

import java.time.OffsetDateTime;

public interface InstanceFlowSummaryProjection {
    Long getSourceApplicationId();

    String getSourceApplicationIntegrationId();

    String getSourceApplicationInstanceId();

    Long getIntegrationId();

    Long getLatestInstanceId();

    OffsetDateTime getLatestUpdate();

    String getLatestStatusEventName();

    String getLatestStorageStatusEventName();

    String getLatestDestinationId();
}
