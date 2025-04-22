package no.fintlabs.repository.utils.instanceflowsummary;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import no.fintlabs.repository.filters.InstanceFlowSummariesQueryFilter;
import no.fintlabs.repository.projections.InstanceFlowSummaryProjection;
import org.testcontainers.shaded.com.google.common.collect.Lists;
import org.testcontainers.shaded.com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static no.fintlabs.repository.utils.instanceflowsummary.Dataset.ALL_EVENTS_AND_EXPECTED_SUMMARIES;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ParameterizedInstanceFlowTestCaseGenerator {

    public static <T> FilterPropertyTestCases createFilterPropertyTestCases(
            BiConsumer<InstanceFlowSummariesQueryFilter.InstanceFlowSummariesQueryFilterBuilder, T> filterModifier,
            List<FilterPropertyTestCaseConfiguration<T>> individualTestCaseConfigurations,
            List<FilterPropertyTestCaseConfiguration<T>> cartesianTestCaseConfigurations
    ) {
        List<FilterPropertyTestCase> individualTestCases = new ArrayList<>();
        individualTestCaseConfigurations
                .stream()
                .map(testCaseConfiguration -> new FilterPropertyTestCase(
                        filterBuilder -> filterModifier.accept(filterBuilder, testCaseConfiguration.getFilterValue()),
                        testCaseConfiguration.getExpectedSummaries()
                )).forEach(individualTestCases::add);

        List<FilterPropertyTestCase> cartesianTestCases = new ArrayList<>();
        cartesianTestCaseConfigurations
                .stream()
                .map(
                        testCaseConfiguration -> new FilterPropertyTestCase(
                                filterBuilder -> filterModifier.accept(filterBuilder, testCaseConfiguration.getFilterValue()),
                                testCaseConfiguration.getExpectedSummaries()
                        )
                )
                .forEach(cartesianTestCases::add);

        return FilterPropertyTestCases
                .builder()
                .individualTestCaseConfigurations(individualTestCases)
                .cartesianTestCaseConfigurations(cartesianTestCases)
                .build();
    }

    public static List<InstanceFlowSummariesTestCase> combineFilterPropertyTestCases(
            List<FilterPropertyTestCases> filterPropertyTestCases
    ) {
        List<FilterPropertyTestCase> individualTestCases = new ArrayList<>();
        List<List<FilterPropertyTestCase>> cartesianTestCases = new ArrayList<>();

        filterPropertyTestCases.forEach(testCases -> {
            individualTestCases.addAll(testCases.getIndividualTestCaseConfigurations());
            cartesianTestCases.add(testCases.getCartesianTestCaseConfigurations());
        });
        return Stream.concat(
                individualTestCases
                        .stream()
                        .map(List::of)
                        .map(ParameterizedInstanceFlowTestCaseGenerator::createInstanceFlowSummariesTestCase),
                combineAndMapToInstanceFlowSummariesTestCases(cartesianTestCases).stream()
        ).toList();
    }

    private static List<InstanceFlowSummariesTestCase> combineAndMapToInstanceFlowSummariesTestCases(
            List<List<FilterPropertyTestCase>> filterModifierAndPredicateCombinations
    ) {
        return Lists.cartesianProduct(
                        filterModifierAndPredicateCombinations
                                .stream()
                                .peek(l -> l.add(0, new FilterPropertyTestCase(
                                        filterBuilder -> {
                                        },
                                        ALL_EVENTS_AND_EXPECTED_SUMMARIES
                                )))
                                .toList()
                )
                .stream()
                .map(ParameterizedInstanceFlowTestCaseGenerator::createInstanceFlowSummariesTestCase)
                .toList();
    }

    private static InstanceFlowSummariesTestCase createInstanceFlowSummariesTestCase(
            List<FilterPropertyTestCase> filterPropertyTestCases
    ) {
        InstanceFlowSummariesQueryFilter.InstanceFlowSummariesQueryFilterBuilder filterBuilder =
                InstanceFlowSummariesQueryFilter.builder();
        filterPropertyTestCases.forEach(
                filterPropertyTestCase ->
                        filterPropertyTestCase.getFilterModifier().accept(filterBuilder)
        );
        return InstanceFlowSummariesTestCase
                .builder()
                .filter(filterBuilder.build())
                .expectedInstanceFlowSummaries(
                        filterPropertyTestCases
                                .stream()
                                .map(FilterPropertyTestCase::getExpectedSummaries)
                                .reduce(Sets::intersection)
                                .orElse(Set.of())
                                .stream()
                                .map(EventEntitiesAndExpectedSummary::getExpectedSummary)
                                .sorted(Comparator.comparing(InstanceFlowSummaryProjection::getLatestUpdate).reversed())
                                .toList()
                )
                .build();
    }

}
