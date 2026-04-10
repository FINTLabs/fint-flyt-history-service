package no.novari.flyt.history.repository.utils.instanceflowsummary

import no.novari.flyt.history.repository.filters.InstanceFlowSummariesQueryFilter
import no.novari.flyt.history.repository.projections.InstanceFlowSummaryProjection

class ParameterizedInstanceFlowTestCaseGenerator private constructor() {
    companion object {
        @JvmStatic
        fun <T> createFilterPropertyTestCases(
            filterModifier: (InstanceFlowSummariesQueryFilter.InstanceFlowSummariesQueryFilterBuilder, T) -> Unit,
            individualTestCaseConfigurations: List<FilterPropertyTestCaseConfiguration<T>>,
            cartesianTestCaseConfigurations: List<FilterPropertyTestCaseConfiguration<T>>,
        ): FilterPropertyTestCases {
            val individualTestCases =
                individualTestCaseConfigurations.map { testCaseConfiguration ->
                    FilterPropertyTestCase(
                        { filterBuilder ->
                            filterModifier(filterBuilder, testCaseConfiguration.filterValue)
                        },
                        testCaseConfiguration.expectedSummaries,
                    )
                }

            val cartesianTestCases =
                cartesianTestCaseConfigurations.map { testCaseConfiguration ->
                    FilterPropertyTestCase(
                        { filterBuilder ->
                            filterModifier(filterBuilder, testCaseConfiguration.filterValue)
                        },
                        testCaseConfiguration.expectedSummaries,
                    )
                }

            return FilterPropertyTestCases
                .builder()
                .individualTestCaseConfigurations(individualTestCases)
                .cartesianTestCaseConfigurations(cartesianTestCases)
                .build()
        }

        @JvmStatic
        fun combineFilterPropertyTestCases(
            filterPropertyTestCases: List<FilterPropertyTestCases>,
        ): List<InstanceFlowSummariesTestCase> {
            val individualTestCases = mutableListOf<FilterPropertyTestCase>()
            val cartesianTestCases = mutableListOf<List<FilterPropertyTestCase>>()

            filterPropertyTestCases.forEach { testCases ->
                individualTestCases += testCases.individualTestCaseConfigurations
                cartesianTestCases += testCases.cartesianTestCaseConfigurations
            }

            return individualTestCases
                .map(::listOf)
                .map(::createInstanceFlowSummariesTestCase) +
                combineAndMapToInstanceFlowSummariesTestCases(cartesianTestCases)
        }

        private fun combineAndMapToInstanceFlowSummariesTestCases(
            filterModifierAndPredicateCombinations: List<List<FilterPropertyTestCase>>,
        ): List<InstanceFlowSummariesTestCase> {
            val lists =
                filterModifierAndPredicateCombinations.map { testCases ->
                    listOf(
                        FilterPropertyTestCase(
                            {},
                            Dataset.ALL_EVENTS_AND_EXPECTED_SUMMARIES,
                        ),
                    ) + testCases
                }

            return cartesianProduct(lists).map(::createInstanceFlowSummariesTestCase)
        }

        private fun createInstanceFlowSummariesTestCase(
            filterPropertyTestCases: List<FilterPropertyTestCase>,
        ): InstanceFlowSummariesTestCase {
            val filterBuilder = InstanceFlowSummariesQueryFilter.builder()
            filterPropertyTestCases.forEach { filterPropertyTestCase ->
                filterPropertyTestCase.filterModifier(filterBuilder)
            }

            return InstanceFlowSummariesTestCase
                .builder()
                .filter(filterBuilder.build())
                .expectedInstanceFlowSummaries(
                    filterPropertyTestCases
                        .map(FilterPropertyTestCase::expectedSummaries)
                        .reduceOrNull(::intersectAll)
                        .orEmpty()
                        .map(EventEntitiesAndExpectedSummary::expectedSummary)
                        .sortedByDescending(InstanceFlowSummaryProjection::latestUpdate),
                ).build()
        }

        private fun intersectAll(
            left: Set<EventEntitiesAndExpectedSummary>,
            right: Set<EventEntitiesAndExpectedSummary>,
        ): Set<EventEntitiesAndExpectedSummary> = left.intersect(right)

        private fun <T> cartesianProduct(lists: List<List<T>>): List<List<T>> {
            return lists.fold(listOf(emptyList())) { acc, list ->
                acc.flatMap { prefix -> list.map(prefix::plus) }
            }
        }
    }
}
