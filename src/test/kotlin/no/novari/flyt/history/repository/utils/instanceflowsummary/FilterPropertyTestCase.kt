package no.novari.flyt.history.repository.utils.instanceflowsummary

import no.novari.flyt.history.repository.filters.InstanceFlowSummariesQueryFilter

class FilterPropertyTestCase(
    val filterModifier: (InstanceFlowSummariesQueryFilter.InstanceFlowSummariesQueryFilterBuilder) -> Unit,
    val expectedSummaries: Set<EventEntitiesAndExpectedSummary>,
)
