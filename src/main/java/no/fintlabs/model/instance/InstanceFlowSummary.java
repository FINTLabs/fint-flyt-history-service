package no.fintlabs.model.instance;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.OffsetDateTime;

@ToString
@Getter
@Builder
@EqualsAndHashCode
public class InstanceFlowSummary {
    private final Long sourceApplicationId;

    private final String sourceApplicationIntegrationId;

    private final String sourceApplicationInstanceId;

    private final Long integrationId;

    private final Long latestInstanceId;

    private final OffsetDateTime latestUpdate;

    private final InstanceStatus status;

    private final InstanceStorageStatus intermediateStorageStatus;

    private final String destinationInstanceIds;

}
