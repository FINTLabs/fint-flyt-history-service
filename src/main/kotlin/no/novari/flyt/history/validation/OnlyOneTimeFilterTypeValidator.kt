package no.novari.flyt.history.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import no.novari.flyt.history.model.time.TimeFilter

class OnlyOneTimeFilterTypeValidator : ConstraintValidator<OnlyOneTimeFilterType, TimeFilter> {
    override fun isValid(
        timeFilter: TimeFilter?,
        context: ConstraintValidatorContext?,
    ): Boolean {
        if (timeFilter == null) {
            return true
        }

        return listOf(timeFilter.offset, timeFilter.currentPeriod, timeFilter.manual)
            .count { it != null } < 2
    }
}
