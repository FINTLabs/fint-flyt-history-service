package no.novari.flyt.history.repository.projections

import java.time.OffsetDateTime

data class InstanceFlowSummaryProjection(
    val sourceApplicationId: Long? = null,
    val sourceApplicationIntegrationId: String? = null,
    val sourceApplicationInstanceId: String? = null,
    val integrationId: Long? = null,
    val latestInstanceId: Long? = null,
    val latestUpdate: OffsetDateTime? = null,
    val latestStatusEventName: String? = null,
    val latestStorageStatusEventName: String? = null,
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
        private var latestStatusEventName: String? = null
        private var latestStorageStatusEventName: String? = null
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

        fun latestStatusEventName(latestStatusEventName: String?) =
            apply {
                this.latestStatusEventName = latestStatusEventName
            }

        fun latestStorageStatusEventName(latestStorageStatusEventName: String?) =
            apply {
                this.latestStorageStatusEventName = latestStorageStatusEventName
            }

        fun destinationInstanceIds(destinationInstanceIds: String?) =
            apply {
                this.destinationInstanceIds = destinationInstanceIds
            }

        fun build() =
            InstanceFlowSummaryProjection(
                sourceApplicationId = sourceApplicationId,
                sourceApplicationIntegrationId = sourceApplicationIntegrationId,
                sourceApplicationInstanceId = sourceApplicationInstanceId,
                integrationId = integrationId,
                latestInstanceId = latestInstanceId,
                latestUpdate = latestUpdate,
                latestStatusEventName = latestStatusEventName,
                latestStorageStatusEventName = latestStorageStatusEventName,
                destinationInstanceIds = destinationInstanceIds,
            )
    }
}
