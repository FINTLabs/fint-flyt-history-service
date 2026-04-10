package no.novari.flyt.history.repository.utils.instanceflowsummary

import no.novari.flyt.history.repository.entities.EventEntity
import no.novari.flyt.history.repository.projections.InstanceFlowSummaryProjection

class EventEntitiesAndExpectedSummary(
    val eventEntities: List<EventEntity>,
    val expectedSummary: InstanceFlowSummaryProjection,
)
