package no.novari.flyt.history.repository.utils.instanceflowsummary

class FilterPropertyTestCaseConfiguration<T>(
    val filterValue: T,
    val expectedSummaries: Set<EventEntitiesAndExpectedSummary>,
)
