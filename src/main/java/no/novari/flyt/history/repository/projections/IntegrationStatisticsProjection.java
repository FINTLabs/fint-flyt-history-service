package no.novari.flyt.history.repository.projections;


public interface IntegrationStatisticsProjection extends InstanceStatisticsProjection {
    Long getIntegrationId();
}
