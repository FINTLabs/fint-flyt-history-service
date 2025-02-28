package no.fintlabs.repository.projections;

public interface InstanceStatisticsProjection {
    Long getTotal();

    Long getInProgress();

    Long getTransferred();

    Long getAborted();

    Long getFailed();

}
