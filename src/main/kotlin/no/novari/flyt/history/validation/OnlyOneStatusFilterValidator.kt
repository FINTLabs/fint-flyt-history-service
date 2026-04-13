package no.novari.flyt.history.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import no.novari.flyt.history.model.instance.InstanceFlowSummariesFilter

class OnlyOneStatusFilterValidator : ConstraintValidator<OnlyOneStatusFilter, InstanceFlowSummariesFilter> {
    override fun isValid(
        instanceFlowSummariesFilter: InstanceFlowSummariesFilter?,
        context: ConstraintValidatorContext?,
    ): Boolean {
        if (instanceFlowSummariesFilter == null) {
            return true
        }

        return instanceFlowSummariesFilter.statuses == null || instanceFlowSummariesFilter.latestStatusEvents == null
    }
}
