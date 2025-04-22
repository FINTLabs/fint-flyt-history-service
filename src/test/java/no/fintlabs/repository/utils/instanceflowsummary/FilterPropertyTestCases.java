package no.fintlabs.repository.utils.instanceflowsummary;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public final class FilterPropertyTestCases {
    private final List<FilterPropertyTestCase> individualTestCaseConfigurations;
    private final List<FilterPropertyTestCase> cartesianTestCaseConfigurations;
}
