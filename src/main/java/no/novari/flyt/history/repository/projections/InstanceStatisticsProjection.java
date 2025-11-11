package no.novari.flyt.history.repository.projections;

public interface InstanceStatisticsProjection {
    Long getTotal();

    Long getInProgress();

    Long getTransferred();

    Long getAborted();

    Long getFailed();

}
