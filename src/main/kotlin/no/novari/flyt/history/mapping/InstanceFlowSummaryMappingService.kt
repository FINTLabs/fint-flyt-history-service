package no.novari.flyt.history.mapping

import no.novari.flyt.history.model.event.EventCategorizationService
import no.novari.flyt.history.model.instance.InstanceFlowSummary
import no.novari.flyt.history.model.instance.InstanceStorageStatus
import no.novari.flyt.history.repository.projections.InstanceFlowSummaryProjection
import org.springframework.stereotype.Service

@Service
class InstanceFlowSummaryMappingService(
    private val eventCategorizationService: EventCategorizationService,
) {
    fun toInstanceFlowSummary(projection: InstanceFlowSummaryProjection?): InstanceFlowSummary {
        requireNotNull(projection) { "Projection is null" }

        return InstanceFlowSummary(
            sourceApplicationId = projection.sourceApplicationId,
            sourceApplicationIntegrationId = projection.sourceApplicationIntegrationId,
            sourceApplicationInstanceId = projection.sourceApplicationInstanceId,
            integrationId = projection.integrationId,
            latestInstanceId = projection.latestInstanceId,
            latestUpdate = projection.latestUpdate,
            status = projection.latestStatusEventName?.let(eventCategorizationService::getStatusByEventName),
            intermediateStorageStatus =
                projection.latestStorageStatusEventName?.let(eventCategorizationService::getStorageStatusByEventName)
                    ?: InstanceStorageStatus.NEVER_STORED,
            destinationInstanceIds = projection.destinationInstanceIds,
        )
    }
}
