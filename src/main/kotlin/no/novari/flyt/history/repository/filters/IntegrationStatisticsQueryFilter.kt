package no.novari.flyt.history.repository.filters

data class IntegrationStatisticsQueryFilter(
    val sourceApplicationIds: Collection<Long>? = null,
    val sourceApplicationIntegrationIds: Collection<String>? = null,
    val integrationIds: Collection<Long>? = null,
) {
    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    class Builder {
        private var sourceApplicationIdsValue: Collection<Long>? = null
        private var sourceApplicationIntegrationIdsValue: Collection<String>? = null
        private var integrationIdsValue: Collection<Long>? = null

        fun sourceApplicationIds(sourceApplicationIds: Collection<Long>?) =
            apply {
                this.sourceApplicationIdsValue = sourceApplicationIds
            }

        fun sourceApplicationIntegrationIds(sourceApplicationIntegrationIds: Collection<String>?) =
            apply {
                this.sourceApplicationIntegrationIdsValue = sourceApplicationIntegrationIds
            }

        fun integrationIds(integrationIds: Collection<Long>?) =
            apply {
                this.integrationIdsValue = integrationIds
            }

        fun build() =
            IntegrationStatisticsQueryFilter(
                sourceApplicationIds = sourceApplicationIdsValue,
                sourceApplicationIntegrationIds = sourceApplicationIntegrationIdsValue,
                integrationIds = integrationIdsValue,
            )
    }
}
