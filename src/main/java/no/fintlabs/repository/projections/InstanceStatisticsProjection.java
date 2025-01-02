package no.fintlabs.repository.projections;

// TODO 20/12/2024 eivindmorch: Include stored, stored and deleted, and never stored?
public interface InstanceStatisticsProjection {
    Long getNumberOfInstances();

    Long getNumberOfInProgressInstances();

    Long getNumberOfTransferredInstances(); // TODO 20/12/2024 eivindmorch: Rename to successful?

    Long getNumberOfRejectedInstances(); // TODO 20/12/2024 eivindmorch: Rename to aborted?

    Long getNumberOfFailedInstances();

}
