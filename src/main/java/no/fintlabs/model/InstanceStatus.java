package no.fintlabs.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class InstanceStatus {
    private final Long sourceApplicationId;

    private final String sourceApplicationIntegrationId;

    private final String sourceApplicationInstanceId;

    private final Long integrationId;

    private final OffsetDateTime timestamp;

    private final String status;

    private final String intermediateStorageStatus;

    private final String destinationId;

}
