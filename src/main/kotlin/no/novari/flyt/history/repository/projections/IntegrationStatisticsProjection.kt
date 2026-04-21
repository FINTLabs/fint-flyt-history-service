package no.novari.flyt.history.repository.projections

interface IntegrationStatisticsProjection : InstanceStatisticsProjection {
    fun getSourceApplicationId(): Long?

    fun getIntegrationId(): Long?
}
