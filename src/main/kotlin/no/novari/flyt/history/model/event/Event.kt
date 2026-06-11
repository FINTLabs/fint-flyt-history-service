package no.novari.flyt.history.model.event

import com.fasterxml.jackson.annotation.JsonProperty
import no.novari.flyt.history.repository.entities.ErrorEntity
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import java.time.OffsetDateTime

data class Event(
    val instanceFlowHeaders: InstanceFlowHeaders? = null,
    val category: EventCategory? = null,
    val timestamp: OffsetDateTime? = null,
    @get:JsonProperty("isScrubbed")
    @param:JsonProperty("isScrubbed")
    val isScrubbed: Boolean = false,
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
        private var isScrubbedValue: Boolean = false
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

        fun isScrubbed(isScrubbed: Boolean) =
            apply {
                this.isScrubbedValue = isScrubbed
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
                isScrubbed = isScrubbedValue,
                type = type,
                applicationId = applicationId,
                errors = errors,
            )
    }
}
