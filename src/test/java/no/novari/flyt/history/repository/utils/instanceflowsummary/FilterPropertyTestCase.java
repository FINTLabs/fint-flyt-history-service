package no.novari.flyt.history.repository.utils.instanceflowsummary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import no.novari.flyt.history.repository.filters.InstanceFlowSummariesQueryFilter;

import java.util.Set;
import java.util.function.Consumer;

@Getter
@AllArgsConstructor
public class FilterPropertyTestCase {
    private final Consumer<InstanceFlowSummariesQueryFilter.InstanceFlowSummariesQueryFilterBuilder> filterModifier;
    private final Set<EventEntitiesAndExpectedSummary> expectedSummaries;
}
