package no.novari.flyt.history.model.statistics

data class IntegrationStatisticsFilter(
    val sourceApplicationIds: Collection<Long>? = null,
    val sourceApplicationIntegrationIds: Collection<String>? = null,
    val integrationIds: Collection<Long>? = null,
) {
    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    class Builder {
        private var sourceApplicationIds: Collection<Long>? = null
        private var sourceApplicationIntegrationIds: Collection<String>? = null
        private var integrationIds: Collection<Long>? = null

        fun sourceApplicationIds(sourceApplicationIds: Collection<Long>?) =
            apply {
                this.sourceApplicationIds = sourceApplicationIds
            }

        fun sourceApplicationIntegrationIds(sourceApplicationIntegrationIds: Collection<String>?) =
            apply {
                this.sourceApplicationIntegrationIds = sourceApplicationIntegrationIds
            }

        fun integrationIds(integrationIds: Collection<Long>?) =
            apply {
                this.integrationIds = integrationIds
            }

        fun build() =
            IntegrationStatisticsFilter(
                sourceApplicationIds = sourceApplicationIds,
                sourceApplicationIntegrationIds = sourceApplicationIntegrationIds,
                integrationIds = integrationIds,
            )
    }
}
