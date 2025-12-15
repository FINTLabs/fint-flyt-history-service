package no.novari.flyt.history.model;

public interface SourceApplicationAggregateInstanceId {
    Long getSourceApplicationId();

    String getSourceApplicationIntegrationId();

    String getSourceApplicationInstanceId();
}
