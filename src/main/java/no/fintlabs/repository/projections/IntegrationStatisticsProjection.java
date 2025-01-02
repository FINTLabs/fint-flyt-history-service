package no.fintlabs.repository.projections;


import lombok.AllArgsConstructor;
import lombok.Getter;

// TODO 20/12/2024 eivindmorch: Interface?
@Getter
@AllArgsConstructor
public class IntegrationStatisticsProjection {
    private final Long integrationId;

    private final InstanceStatisticsProjection instanceStatisticsProjection;

    public IntegrationStatisticsProjection(
            Long integrationId,
            Long numberOfInstances,
            Long numberOfInProgressInstances,
            Long numberOfTransferredInstances,
            Long numberOfRejectedInstances,
            Long numberOfFailedInstances
    ) {
        this.integrationId = integrationId;
        instanceStatisticsProjection = new InstanceStatisticsProjection(
                numberOfInstances,
                numberOfInProgressInstances,
                numberOfTransferredInstances,
                numberOfRejectedInstances,
                numberOfFailedInstances
        );
    }
}
