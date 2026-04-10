package no.novari.flyt.history.model.event

import no.novari.flyt.history.repository.entities.ErrorEntity
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import java.time.OffsetDateTime

data class Event(
    val instanceFlowHeaders: InstanceFlowHeaders? = null,
    val category: EventCategory? = null,
    val timestamp: OffsetDateTime? = null,
    val type: EventType? = null,
    val applicationId: String? = null,
    val errors: Collection<ErrorEntity> = emptyList(),
) {
    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    class Builder {
        private var instanceFlowHeaders: InstanceFlowHeaders? = null
        private var category: EventCategory? = null
        private var timestamp: OffsetDateTime? = null
        private var type: EventType? = null
        private var applicationId: String? = null
        private var errors: Collection<ErrorEntity> = emptyList()

        fun instanceFlowHeaders(instanceFlowHeaders: InstanceFlowHeaders?) =
            apply {
                this.instanceFlowHeaders = instanceFlowHeaders
            }

        fun category(category: EventCategory?) =
            apply {
                this.category = category
            }

        fun timestamp(timestamp: OffsetDateTime?) =
            apply {
                this.timestamp = timestamp
            }

        fun type(type: EventType?) =
            apply {
                this.type = type
            }

        fun applicationId(applicationId: String?) =
            apply {
                this.applicationId = applicationId
            }

        fun errors(errors: Collection<ErrorEntity>?) =
            apply {
                this.errors = errors ?: emptyList()
            }

        fun build() =
            Event(
                instanceFlowHeaders = instanceFlowHeaders,
                category = category,
                timestamp = timestamp,
                type = type,
                applicationId = applicationId,
                errors = errors,
            )
    }
}
