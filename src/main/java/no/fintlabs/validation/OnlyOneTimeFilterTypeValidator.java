package no.fintlabs.validation;

import no.fintlabs.model.time.TimeFilter;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Optional;
import java.util.stream.Stream;

public class OnlyOneTimeFilterTypeValidator implements ConstraintValidator<OnlyOneTimeFilterType, TimeFilter> {

    @Override
    public boolean isValid(TimeFilter timeFilter, ConstraintValidatorContext constraintValidatorContext) {
        if (timeFilter == null) {
            return true;
        }
        return Stream.of(
                        timeFilter.getOffset(),
                        timeFilter.getCurrentPeriod(),
                        timeFilter.getManual()
                )
                       .map(Optional::ofNullable)
                       .filter(Optional::isPresent)
                       .count() < 2;
    }

}
