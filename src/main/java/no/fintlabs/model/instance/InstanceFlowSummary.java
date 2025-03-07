package no.fintlabs.model.instance;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.OffsetDateTime;

@ToString
@Getter
@Builder
public class InstanceFlowSummary {
    private final Long sourceApplicationId;

    private final String sourceApplicationIntegrationId;

    private final String sourceApplicationInstanceId;

    private final Long integrationId;

    private final OffsetDateTime latestUpdate;

    private final InstanceStatus status;

    private final InstanceStorageStatus intermediateStorageStatus;

    private final String destinationId;

    // TODO 28/02/2025 eivindmorch: Missing instanceId (latestInstanceId)
}
