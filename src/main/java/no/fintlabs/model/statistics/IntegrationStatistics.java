package no.fintlabs.model.statistics;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class IntegrationStatistics {
    private final Long integrationId;

    private final Long numberOfCurrentStatuses;

    private final Long numberOfCurrentDispatchedStatuses;

    private final Long numberOfCurrentInProgressStatuses;

    private final Long numberOfCurrentErrorStatuses;
}
