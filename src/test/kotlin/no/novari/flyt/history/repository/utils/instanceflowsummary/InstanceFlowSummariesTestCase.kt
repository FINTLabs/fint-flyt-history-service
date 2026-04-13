package no.novari.flyt.history.repository.utils.instanceflowsummary

import no.novari.flyt.history.repository.filters.InstanceFlowSummariesQueryFilter
import no.novari.flyt.history.repository.projections.InstanceFlowSummaryProjection

class InstanceFlowSummariesTestCase(
    val filter: InstanceFlowSummariesQueryFilter,
    val expectedInstanceFlowSummaries: List<InstanceFlowSummaryProjection>,
) {
    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    class Builder {
        private var filter: InstanceFlowSummariesQueryFilter? = null
        private var expectedInstanceFlowSummaries: List<InstanceFlowSummaryProjection> = emptyList()

        fun filter(filter: InstanceFlowSummariesQueryFilter) = apply { this.filter = filter }

        fun expectedInstanceFlowSummaries(expectedInstanceFlowSummaries: List<InstanceFlowSummaryProjection>) =
            apply { this.expectedInstanceFlowSummaries = expectedInstanceFlowSummaries }

        fun build() =
            InstanceFlowSummariesTestCase(
                filter = requireNotNull(filter),
                expectedInstanceFlowSummaries = expectedInstanceFlowSummaries,
            )
    }
}
