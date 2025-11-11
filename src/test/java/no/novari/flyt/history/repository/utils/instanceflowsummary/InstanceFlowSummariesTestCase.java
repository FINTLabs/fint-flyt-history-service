package no.novari.flyt.history.repository.utils.instanceflowsummary;

import lombok.Builder;
import lombok.Getter;
import no.novari.flyt.history.repository.filters.InstanceFlowSummariesQueryFilter;
import no.novari.flyt.history.repository.projections.InstanceFlowSummaryProjection;

import java.util.List;

@Getter
@Builder
public class InstanceFlowSummariesTestCase {
    private final InstanceFlowSummariesQueryFilter filter;
    private final List<InstanceFlowSummaryProjection> expectedInstanceFlowSummaries;
}
