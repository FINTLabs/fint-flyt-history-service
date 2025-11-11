package no.novari.flyt.history.repository.utils.instanceflowsummary;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

@Getter
@AllArgsConstructor
public final class FilterPropertyTestCaseConfiguration<T> {
    private final T filterValue;
    private final Set<EventEntitiesAndExpectedSummary> expectedSummaries;
}
