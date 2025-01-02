package no.fintlabs.repository.projections;

public interface InstanceStatisticsProjection {
    Long getNumberOfInstances();

    Long getNumberOfInProgressInstances();

    Long getNumberOfTransferredInstances();

    Long getNumberOfAbortedInstances();

    Long getNumberOfFailedInstances();

}
