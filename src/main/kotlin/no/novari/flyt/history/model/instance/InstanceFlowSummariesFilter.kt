package no.novari.flyt.history.model.instance

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.time.TimeFilter
import no.novari.flyt.history.validation.OnlyOneStatusFilter
import java.time.ZoneId

@OnlyOneStatusFilter
data class InstanceFlowSummariesFilter(
    @field:Valid
    val time: TimeFilter? = null,
    val sourceApplicationIds: Collection<Long>? = null,
    val sourceApplicationIntegrationIds: Collection<@NotBlank String>? = null,
    val sourceApplicationInstanceIds: Collection<@NotBlank String>? = null,
    val integrationIds: Collection<Long>? = null,
    val statuses: Collection<InstanceStatus>? = null,
    val latestStatusEvents: Collection<EventCategory>? = null,
    val storageStatuses: Collection<InstanceStorageStatus>? = null,
    val associatedEvents: Collection<EventCategory>? = null,
    val destinationIds: Collection<@NotBlank String>? = null,
    val timeZone: ZoneId = ZoneId.of("Europe/Oslo"),
) {
    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    class Builder {
        private var time: TimeFilter? = null
        private var sourceApplicationIds: Collection<Long>? = null
        private var sourceApplicationIntegrationIds: Collection<String>? = null
        private var sourceApplicationInstanceIds: Collection<String>? = null
        private var integrationIds: Collection<Long>? = null
        private var statuses: Collection<InstanceStatus>? = null
        private var latestStatusEvents: Collection<EventCategory>? = null
        private var storageStatuses: Collection<InstanceStorageStatus>? = null
        private var associatedEvents: Collection<EventCategory>? = null
        private var destinationIds: Collection<String>? = null

        fun time(time: TimeFilter?) = apply { this.time = time }

        fun sourceApplicationIds(sourceApplicationIds: Collection<Long>?) =
            apply {
                this.sourceApplicationIds = sourceApplicationIds
            }

        fun sourceApplicationIntegrationIds(sourceApplicationIntegrationIds: Collection<String>?) =
            apply {
                this.sourceApplicationIntegrationIds = sourceApplicationIntegrationIds
            }

        fun sourceApplicationInstanceIds(sourceApplicationInstanceIds: Collection<String>?) =
            apply {
                this.sourceApplicationInstanceIds = sourceApplicationInstanceIds
            }

        fun integrationIds(integrationIds: Collection<Long>?) =
            apply {
                this.integrationIds = integrationIds
            }

        fun statuses(statuses: Collection<InstanceStatus>?) = apply { this.statuses = statuses }

        fun latestStatusEvents(latestStatusEvents: Collection<EventCategory>?) =
            apply {
                this.latestStatusEvents = latestStatusEvents
            }

        fun storageStatuses(storageStatuses: Collection<InstanceStorageStatus>?) =
            apply {
                this.storageStatuses = storageStatuses
            }

        fun associatedEvents(associatedEvents: Collection<EventCategory>?) =
            apply {
                this.associatedEvents = associatedEvents
            }

        fun destinationIds(destinationIds: Collection<String>?) = apply { this.destinationIds = destinationIds }

        fun build() =
            InstanceFlowSummariesFilter(
                time = time,
                sourceApplicationIds = sourceApplicationIds,
                sourceApplicationIntegrationIds = sourceApplicationIntegrationIds,
                sourceApplicationInstanceIds = sourceApplicationInstanceIds,
                integrationIds = integrationIds,
                statuses = statuses,
                latestStatusEvents = latestStatusEvents,
                storageStatuses = storageStatuses,
                associatedEvents = associatedEvents,
                destinationIds = destinationIds,
            )
    }
}
