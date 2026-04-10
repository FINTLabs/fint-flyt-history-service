package no.novari.flyt.history.mapping

import jakarta.validation.Validator
import no.novari.flyt.history.model.event.EventCategorizationService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.instance.InstanceFlowSummariesFilter
import no.novari.flyt.history.model.instance.InstanceStorageStatus
import no.novari.flyt.history.repository.filters.InstanceFlowSummariesQueryFilter
import no.novari.flyt.history.repository.filters.InstanceStorageStatusQueryFilter
import org.springframework.stereotype.Service

@Service
class InstanceFlowSummariesFilterMappingService(
    private val validator: Validator,
    private val eventCategorizationService: EventCategorizationService,
    private val timeFilterMappingService: TimeFilterMappingService,
) {
    fun toQueryFilter(instanceFlowSummariesFilter: InstanceFlowSummariesFilter?): InstanceFlowSummariesQueryFilter {
        requireNotNull(instanceFlowSummariesFilter) { "Instance flow summaries filter is null" }

        val validationResult = validator.validate(instanceFlowSummariesFilter)
        require(validationResult.isEmpty()) { "Invalid instance flow summaries filter: $validationResult" }

        return InstanceFlowSummariesQueryFilter
            .builder()
            .sourceApplicationIds(instanceFlowSummariesFilter.sourceApplicationIds)
            .sourceApplicationIntegrationIds(instanceFlowSummariesFilter.sourceApplicationIntegrationIds)
            .sourceApplicationInstanceIds(instanceFlowSummariesFilter.sourceApplicationInstanceIds)
            .integrationIds(instanceFlowSummariesFilter.integrationIds)
            .statusEventNames(
                instanceFlowSummariesFilter.statuses?.let(eventCategorizationService::getEventNamesByInstanceStatuses)
                    ?: instanceFlowSummariesFilter.latestStatusEvents?.mapTo(linkedSetOf(), EventCategory::eventName),
            ).instanceStorageStatusQueryFilter(
                instanceFlowSummariesFilter.storageStatuses?.let { storageStatuses ->
                    InstanceStorageStatusQueryFilter(
                        eventCategorizationService.getEventNamesByInstanceStorageStatuses(storageStatuses),
                        storageStatuses.contains(InstanceStorageStatus.NEVER_STORED),
                    )
                } ?: InstanceStorageStatusQueryFilter.EMPTY,
            ).associatedEventNames(instanceFlowSummariesFilter.associatedEvents?.map(EventCategory::eventName))
            .destinationIds(instanceFlowSummariesFilter.destinationIds)
            .timeQueryFilter(
                instanceFlowSummariesFilter.time?.let {
                    timeFilterMappingService.toQueryFilter(it, instanceFlowSummariesFilter.timeZone)
                },
            ).build()
    }
}
