package no.novari.flyt.history.repository.projections;

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

    String getDestinationInstanceIds();
}
