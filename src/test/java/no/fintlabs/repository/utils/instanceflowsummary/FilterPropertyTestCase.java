package no.fintlabs.repository.utils.instanceflowsummary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import no.fintlabs.repository.filters.InstanceFlowSummariesQueryFilter;

import java.util.Set;
import java.util.function.Consumer;

@Getter
@AllArgsConstructor
public class FilterPropertyTestCase {
    private final Consumer<InstanceFlowSummariesQueryFilter.InstanceFlowSummariesQueryFilterBuilder> filterModifier;
    private final Set<EventEntitiesAndExpectedSummary> expectedSummaries;
}
