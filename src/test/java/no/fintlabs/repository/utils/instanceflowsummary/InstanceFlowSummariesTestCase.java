package no.fintlabs.repository.utils.instanceflowsummary;

import lombok.Builder;
import lombok.Getter;
import no.fintlabs.repository.filters.InstanceFlowSummariesQueryFilter;
import no.fintlabs.repository.projections.InstanceFlowSummaryProjection;

import java.util.List;

@Getter
@Builder
public class InstanceFlowSummariesTestCase {
    private final InstanceFlowSummariesQueryFilter filter;
    private final List<InstanceFlowSummaryProjection> expectedInstanceFlowSummaries;
}
