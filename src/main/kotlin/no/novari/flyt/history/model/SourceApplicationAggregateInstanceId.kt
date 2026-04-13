package no.novari.flyt.history.model

interface SourceApplicationAggregateInstanceId {
    val sourceApplicationId: Long?

    val sourceApplicationIntegrationId: String?

    val sourceApplicationInstanceId: String?
}
