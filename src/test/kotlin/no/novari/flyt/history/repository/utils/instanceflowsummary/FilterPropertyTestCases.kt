package no.novari.flyt.history.repository.utils.instanceflowsummary

class FilterPropertyTestCases(
    val individualTestCaseConfigurations: List<FilterPropertyTestCase>,
    val cartesianTestCaseConfigurations: List<FilterPropertyTestCase>,
) {
    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    class Builder {
        private var individualTestCaseConfigurations: List<FilterPropertyTestCase> = emptyList()
        private var cartesianTestCaseConfigurations: List<FilterPropertyTestCase> = emptyList()

        fun individualTestCaseConfigurations(individualTestCaseConfigurations: List<FilterPropertyTestCase>) =
            apply { this.individualTestCaseConfigurations = individualTestCaseConfigurations }

        fun cartesianTestCaseConfigurations(cartesianTestCaseConfigurations: List<FilterPropertyTestCase>) =
            apply { this.cartesianTestCaseConfigurations = cartesianTestCaseConfigurations }

        fun build() =
            FilterPropertyTestCases(
                individualTestCaseConfigurations = individualTestCaseConfigurations,
                cartesianTestCaseConfigurations = cartesianTestCaseConfigurations,
            )
    }
}
