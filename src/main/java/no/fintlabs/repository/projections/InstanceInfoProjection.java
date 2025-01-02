package no.fintlabs.repository.projections;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

@AllArgsConstructor
@Getter
public class InstanceInfoProjection {
    private final Long sourceApplicationId;

    private final String sourceApplicationIntegrationId;

    private final String sourceApplicationInstanceId;

    private final Long integrationId;

    private final OffsetDateTime latestUpdate;

    private final String latestStatusEventName;

    private final String latestStorageStatusEventName;

    private final String destinationId;
}
