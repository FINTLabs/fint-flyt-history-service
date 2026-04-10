package no.novari.flyt.history.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import no.novari.flyt.history.model.time.ManualTimeFilter

class MinTimestampBeforeMaxTimestampValidator : ConstraintValidator<MinTimestampBeforeMaxTimestamp, ManualTimeFilter> {
    override fun isValid(
        manualTimeFilter: ManualTimeFilter?,
        context: ConstraintValidatorContext?,
    ): Boolean {
        val min = manualTimeFilter?.min ?: return true
        val max = manualTimeFilter.max ?: return true
        return min.isBefore(max)
    }
}
