package no.novari.flyt.history.validation;

import no.novari.flyt.history.model.time.ManualTimeFilter;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Optional;

public class MinTimestampBeforeMaxTimestampValidator implements ConstraintValidator<MinTimestampBeforeMaxTimestamp, ManualTimeFilter> {

    @Override
    public boolean isValid(ManualTimeFilter manualTimeFilter, ConstraintValidatorContext constraintValidatorContext) {
        return Optional.ofNullable(manualTimeFilter.getMin())
                .flatMap(min -> Optional.ofNullable(manualTimeFilter.getMax()).map(min::isBefore))
                .orElse(true);
    }

}
