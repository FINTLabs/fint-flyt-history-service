package no.novari.flyt.history.repository.projections

interface IntegrationStatisticsProjection : InstanceStatisticsProjection {
    fun getIntegrationId(): Long?
}
