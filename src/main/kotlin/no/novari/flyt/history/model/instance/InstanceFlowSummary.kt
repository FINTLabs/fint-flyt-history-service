package no.novari.flyt.history.model.instance

import java.time.OffsetDateTime

data class InstanceFlowSummary(
    val sourceApplicationId: Long? = null,
    val sourceApplicationIntegrationId: String? = null,
    val sourceApplicationInstanceId: String? = null,
    val integrationId: Long? = null,
    val latestInstanceId: Long? = null,
    val latestUpdate: OffsetDateTime? = null,
    val status: InstanceStatus? = null,
    val intermediateStorageStatus: InstanceStorageStatus? = null,
    val destinationInstanceIds: String? = null,
) {
    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    class Builder {
        private var sourceApplicationId: Long? = null
        private var sourceApplicationIntegrationId: String? = null
        private var sourceApplicationInstanceId: String? = null
        private var integrationId: Long? = null
        private var latestInstanceId: Long? = null
        private var latestUpdate: OffsetDateTime? = null
        private var status: InstanceStatus? = null
        private var intermediateStorageStatus: InstanceStorageStatus? = null
        private var destinationInstanceIds: String? = null

        fun sourceApplicationId(sourceApplicationId: Long?) = apply { this.sourceApplicationId = sourceApplicationId }

        fun sourceApplicationIntegrationId(sourceApplicationIntegrationId: String?) =
            apply {
                this.sourceApplicationIntegrationId = sourceApplicationIntegrationId
            }

        fun sourceApplicationInstanceId(sourceApplicationInstanceId: String?) =
            apply {
                this.sourceApplicationInstanceId = sourceApplicationInstanceId
            }

        fun integrationId(integrationId: Long?) = apply { this.integrationId = integrationId }

        fun latestInstanceId(latestInstanceId: Long?) = apply { this.latestInstanceId = latestInstanceId }

        fun latestUpdate(latestUpdate: OffsetDateTime?) = apply { this.latestUpdate = latestUpdate }

        fun status(status: InstanceStatus?) = apply { this.status = status }

        fun intermediateStorageStatus(intermediateStorageStatus: InstanceStorageStatus?) =
            apply {
                this.intermediateStorageStatus = intermediateStorageStatus
            }

        fun destinationInstanceIds(destinationInstanceIds: String?) =
            apply {
                this.destinationInstanceIds = destinationInstanceIds
            }

        fun build() =
            InstanceFlowSummary(
                sourceApplicationId = sourceApplicationId,
                sourceApplicationIntegrationId = sourceApplicationIntegrationId,
                sourceApplicationInstanceId = sourceApplicationInstanceId,
                integrationId = integrationId,
                latestInstanceId = latestInstanceId,
                latestUpdate = latestUpdate,
                status = status,
                intermediateStorageStatus = intermediateStorageStatus,
                destinationInstanceIds = destinationInstanceIds,
            )
    }
}
