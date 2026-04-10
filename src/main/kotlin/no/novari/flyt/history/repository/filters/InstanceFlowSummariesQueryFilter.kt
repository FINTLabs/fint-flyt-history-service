package no.novari.flyt.history.repository.filters

data class InstanceFlowSummariesQueryFilter(
    val sourceApplicationIds: Collection<Long>? = null,
    val sourceApplicationIntegrationIds: Collection<String>? = null,
    val sourceApplicationInstanceIds: Collection<String>? = null,
    val integrationIds: Collection<Long>? = null,
    val statusEventNames: Collection<String>? = null,
    val instanceStorageStatusQueryFilter: InstanceStorageStatusQueryFilter? = InstanceStorageStatusQueryFilter.EMPTY,
    val associatedEventNames: Collection<String>? = null,
    val destinationIds: Collection<String>? = null,
    val timeQueryFilter: TimeQueryFilter? = null,
) {
    companion object {
        @JvmStatic
        fun builder() = InstanceFlowSummariesQueryFilterBuilder()
    }

    class InstanceFlowSummariesQueryFilterBuilder {
        private var sourceApplicationIdsValue: Collection<Long>? = null
        private var sourceApplicationIntegrationIdsValue: Collection<String>? = null
        private var sourceApplicationInstanceIdsValue: Collection<String>? = null
        private var integrationIdsValue: Collection<Long>? = null
        private var statusEventNamesValue: Collection<String>? = null
        private var instanceStorageStatusQueryFilterValue: InstanceStorageStatusQueryFilter? =
            InstanceStorageStatusQueryFilter.EMPTY
        private var associatedEventNamesValue: Collection<String>? = null
        private var destinationIdsValue: Collection<String>? = null
        private var timeQueryFilterValue: TimeQueryFilter? = null

        fun sourceApplicationIds(sourceApplicationIds: Collection<Long>?) =
            apply {
                this.sourceApplicationIdsValue = sourceApplicationIds
            }

        fun sourceApplicationIntegrationIds(sourceApplicationIntegrationIds: Collection<String>?) =
            apply {
                this.sourceApplicationIntegrationIdsValue = sourceApplicationIntegrationIds
            }

        fun sourceApplicationInstanceIds(sourceApplicationInstanceIds: Collection<String>?) =
            apply {
                this.sourceApplicationInstanceIdsValue = sourceApplicationInstanceIds
            }

        fun integrationIds(integrationIds: Collection<Long>?) =
            apply {
                this.integrationIdsValue = integrationIds
            }

        fun statusEventNames(statusEventNames: Collection<String>?) =
            apply {
                this.statusEventNamesValue = statusEventNames
            }

        fun instanceStorageStatusQueryFilter(instanceStorageStatusQueryFilter: InstanceStorageStatusQueryFilter?) =
            apply {
                this.instanceStorageStatusQueryFilterValue = instanceStorageStatusQueryFilter
            }

        fun associatedEventNames(associatedEventNames: Collection<String>?) =
            apply {
                this.associatedEventNamesValue = associatedEventNames
            }

        fun destinationIds(destinationIds: Collection<String>?) =
            apply {
                this.destinationIdsValue = destinationIds
            }

        fun timeQueryFilter(timeQueryFilter: TimeQueryFilter?) =
            apply {
                this.timeQueryFilterValue = timeQueryFilter
            }

        fun build() =
            InstanceFlowSummariesQueryFilter(
                sourceApplicationIds = sourceApplicationIdsValue,
                sourceApplicationIntegrationIds = sourceApplicationIntegrationIdsValue,
                sourceApplicationInstanceIds = sourceApplicationInstanceIdsValue,
                integrationIds = integrationIdsValue,
                statusEventNames = statusEventNamesValue,
                instanceStorageStatusQueryFilter = instanceStorageStatusQueryFilterValue,
                associatedEventNames = associatedEventNamesValue,
                destinationIds = destinationIdsValue,
                timeQueryFilter = timeQueryFilterValue,
            )
    }
}
