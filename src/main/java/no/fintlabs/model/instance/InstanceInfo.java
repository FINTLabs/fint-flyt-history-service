package no.fintlabs.model.instance;

import lombok.Getter;
import lombok.ToString;
import no.fintlabs.model.eventinfo.InstanceStatusEvent;
import no.fintlabs.model.eventinfo.InstanceStorageStatusEvent;

import java.time.OffsetDateTime;
import java.util.Optional;

@ToString
@Getter
public class InstanceInfo {
    private final Long sourceApplicationId;

    private final String sourceApplicationIntegrationId;

    private final String sourceApplicationInstanceId;

    private final Long integrationId;

    private final OffsetDateTime latestUpdate;

    private final InstanceStatus status;

    private final InstanceStorageStatus intermediateStorageStatus;

    private final String destinationId;

    public InstanceInfo(
            Long sourceApplicationId,
            String sourceApplicationIntegrationId,
            String sourceApplicationInstanceId,
            Long integrationId,
            OffsetDateTime latestUpdate,
            InstanceStatus status,
            InstanceStorageStatus intermediateStorageStatus,
            String destinationId
    ) {
        this.sourceApplicationId = sourceApplicationId;
        this.sourceApplicationIntegrationId = sourceApplicationIntegrationId;
        this.sourceApplicationInstanceId = sourceApplicationInstanceId;
        this.integrationId = integrationId;
        this.latestUpdate = latestUpdate;
        this.status = status;
        this.intermediateStorageStatus = intermediateStorageStatus;
        this.destinationId = destinationId;
    }

    public InstanceInfo(
            Long sourceApplicationId,
            String sourceApplicationIntegrationId,
            String sourceApplicationInstanceId,
            Long integrationId,
            OffsetDateTime latestUpdate,
            String latestStatusEventName,
            String latestStorageStatusEventName,
            String destinationId
    ) {
        this(
                sourceApplicationId,
                sourceApplicationIntegrationId,
                sourceApplicationInstanceId,
                integrationId,
                latestUpdate,
                InstanceStatusEvent.valueByName(latestStatusEventName).getInstanceStatus(),
                Optional.ofNullable(latestStorageStatusEventName)
                        .map(InstanceStorageStatusEvent::valueByName)
                        .map(InstanceStorageStatusEvent::getStorageStatus)
                        .orElse(InstanceStorageStatus.NEVER_STORED),
                destinationId
        );
    }
}
