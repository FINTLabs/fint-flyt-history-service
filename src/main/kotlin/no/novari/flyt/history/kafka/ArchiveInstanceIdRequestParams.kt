package no.novari.flyt.history.kafka

import no.novari.flyt.history.model.SourceApplicationAggregateInstanceId

data class ArchiveInstanceIdRequestParams(
    override val sourceApplicationId: Long? = null,
    override val sourceApplicationIntegrationId: String? = null,
    override val sourceApplicationInstanceId: String? = null,
) : SourceApplicationAggregateInstanceId {
    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    class Builder {
        private var sourceApplicationId: Long? = null
        private var sourceApplicationIntegrationId: String? = null
        private var sourceApplicationInstanceId: String? = null

        fun sourceApplicationId(sourceApplicationId: Long?) = apply { this.sourceApplicationId = sourceApplicationId }

        fun sourceApplicationIntegrationId(sourceApplicationIntegrationId: String?) =
            apply {
                this.sourceApplicationIntegrationId = sourceApplicationIntegrationId
            }

        fun sourceApplicationInstanceId(sourceApplicationInstanceId: String?) =
            apply {
                this.sourceApplicationInstanceId = sourceApplicationInstanceId
            }

        fun build() =
            ArchiveInstanceIdRequestParams(
                sourceApplicationId = sourceApplicationId,
                sourceApplicationIntegrationId = sourceApplicationIntegrationId,
                sourceApplicationInstanceId = sourceApplicationInstanceId,
            )
    }
}
