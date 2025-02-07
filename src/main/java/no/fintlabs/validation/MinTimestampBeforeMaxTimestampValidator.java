package no.fintlabs.validation;

import no.fintlabs.model.time.ManualTimeFilter;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Optional;

public class MinTimestampBeforeMaxTimestampValidator implements ConstraintValidator<MinTimestampBeforeMaxTimestamp, ManualTimeFilter> {

    @Override
    public boolean isValid(ManualTimeFilter manualTimeFilter, ConstraintValidatorContext constraintValidatorContext) {
        return Optional.ofNullable(manualTimeFilter.getMin())
                .flatMap(min -> Optional.ofNullable(manualTimeFilter.getMax()).map(min::isBefore))
                .orElse(true);
    }

}
