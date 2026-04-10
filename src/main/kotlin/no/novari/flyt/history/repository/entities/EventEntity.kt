package no.novari.flyt.history.repository.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.CascadeType
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import no.novari.flyt.history.model.event.EventType
import java.time.OffsetDateTime

@Entity
@Table(
    name = "event",
    indexes = [
        Index(name = "timestamp_index", columnList = "timestamp"),
        Index(
            name = "source_application_aggregate_id_and_timestamp_and_name_index",
            columnList =
                "sourceApplicationId, sourceApplicationIntegrationId, " +
                    "sourceApplicationInstanceId, timestamp, name",
        ),
    ],
)
class EventEntity(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "event_id_seq_gen")
    @field:SequenceGenerator(name = "event_id_seq_gen", sequenceName = "event_id_seq", allocationSize = 500)
    @get:JsonIgnore
    var id: Long = 0,
    @field:Embedded
    var instanceFlowHeaders: InstanceFlowHeadersEmbeddable? = null,
    var name: String? = null,
    var timestamp: OffsetDateTime? = null,
    @field:Enumerated(EnumType.STRING)
    var type: EventType? = null,
    var applicationId: String? = null,
    @field:OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @field:JoinColumn(name = "event_id")
    var errors: MutableCollection<ErrorEntity> = mutableListOf(),
) {
    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    class Builder {
        private var id: Long = 0
        private var instanceFlowHeaders: InstanceFlowHeadersEmbeddable? = null
        private var name: String? = null
        private var timestamp: OffsetDateTime? = null
        private var type: EventType? = null
        private var applicationId: String? = null
        private var errors: MutableCollection<ErrorEntity> = mutableListOf()

        fun id(id: Long) = apply { this.id = id }

        fun instanceFlowHeaders(instanceFlowHeaders: InstanceFlowHeadersEmbeddable?) =
            apply {
                this.instanceFlowHeaders = instanceFlowHeaders
            }

        fun name(name: String?) = apply { this.name = name }

        fun timestamp(timestamp: OffsetDateTime?) = apply { this.timestamp = timestamp }

        fun type(type: EventType?) = apply { this.type = type }

        fun applicationId(applicationId: String?) = apply { this.applicationId = applicationId }

        fun errors(errors: Collection<ErrorEntity>?) =
            apply {
                this.errors = errors?.toMutableList() ?: mutableListOf()
            }

        fun build() =
            EventEntity(
                id = id,
                instanceFlowHeaders = instanceFlowHeaders,
                name = name,
                timestamp = timestamp,
                type = type,
                applicationId = applicationId,
                errors = errors,
            )
    }
}
