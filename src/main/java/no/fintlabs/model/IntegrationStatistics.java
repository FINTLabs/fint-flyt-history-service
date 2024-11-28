package no.fintlabs.model;

public interface IntegrationStatistics {
    String getIntegrationId();

    Long getNumberOfCurrentStatuses();

    Long getNumberOfCurrentDispatchedStatuses();

    Long getNumberOfCurrentInProgressStatuses();

    Long getNumberOfCurrentErrorStatuses();
}
