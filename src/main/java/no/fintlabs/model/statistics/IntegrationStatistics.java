package no.fintlabs.model.statistics;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class IntegrationStatistics {
    private final Long integrationId;

    private final InstanceStatistics instanceStatistics;

    public IntegrationStatistics(
            Long integrationId,
            Long numberOfInstances,
            Long numberOfTransferredInstances,
            Long numberOfInProgressInstances,
            Long numberOfFailedInstances
    ) {
        this.integrationId = integrationId;
        instanceStatistics = new InstanceStatistics(
                numberOfInstances, numberOfTransferredInstances, numberOfInProgressInstances, numberOfFailedInstances
        );
    }
}
