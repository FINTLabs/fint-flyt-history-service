package no.novari.flyt.history.repository.entities

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.JoinColumn
import java.util.UUID

@Embeddable
data class InstanceFlowHeadersEmbeddable(
    var sourceApplicationId: Long? = null,
    var sourceApplicationIntegrationId: String? = null,
    var sourceApplicationInstanceId: String? = null,
    @field:ElementCollection
    @field:CollectionTable(name = "file_id", joinColumns = [JoinColumn(name = "event_id")])
    @field:Column(name = "file_id")
    var fileIds: MutableList<UUID> = mutableListOf(),
    var correlationId: UUID? = null,
    var integrationId: Long? = null,
    var instanceId: Long? = null,
    var configurationId: Long? = null,
    var archiveInstanceId: String? = null,
) {
    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    class Builder {
        private var sourceApplicationId: Long? = null
        private var sourceApplicationIntegrationId: String? = null
        private var sourceApplicationInstanceId: String? = null
        private var fileIds: MutableList<UUID> = mutableListOf()
        private var correlationId: UUID? = null
        private var integrationId: Long? = null
        private var instanceId: Long? = null
        private var configurationId: Long? = null
        private var archiveInstanceId: String? = null

        fun sourceApplicationId(sourceApplicationId: Long?) = apply { this.sourceApplicationId = sourceApplicationId }

        fun sourceApplicationIntegrationId(sourceApplicationIntegrationId: String?) =
            apply {
                this.sourceApplicationIntegrationId = sourceApplicationIntegrationId
            }

        fun sourceApplicationInstanceId(sourceApplicationInstanceId: String?) =
            apply {
                this.sourceApplicationInstanceId = sourceApplicationInstanceId
            }

        fun fileIds(fileIds: List<UUID>?) = apply { this.fileIds = fileIds?.toMutableList() ?: mutableListOf() }

        fun correlationId(correlationId: UUID?) = apply { this.correlationId = correlationId }

        fun integrationId(integrationId: Long?) = apply { this.integrationId = integrationId }

        fun instanceId(instanceId: Long?) = apply { this.instanceId = instanceId }

        fun configurationId(configurationId: Long?) = apply { this.configurationId = configurationId }

        fun archiveInstanceId(archiveInstanceId: String?) = apply { this.archiveInstanceId = archiveInstanceId }

        fun build() =
            InstanceFlowHeadersEmbeddable(
                sourceApplicationId = sourceApplicationId,
                sourceApplicationIntegrationId = sourceApplicationIntegrationId,
                sourceApplicationInstanceId = sourceApplicationInstanceId,
                fileIds = fileIds,
                correlationId = correlationId,
                integrationId = integrationId,
                instanceId = instanceId,
                configurationId = configurationId,
                archiveInstanceId = archiveInstanceId,
            )
    }
}
