package no.novari.flyt.history.repository.projections

interface InstanceStatisticsProjection {
    fun getTotal(): Long?

    fun getInProgress(): Long?

    fun getTransferred(): Long?

    fun getAborted(): Long?

    fun getFailed(): Long?
}
