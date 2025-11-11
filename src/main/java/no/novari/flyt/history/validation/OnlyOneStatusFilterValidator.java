package no.novari.flyt.history.validation;

import no.novari.flyt.history.model.instance.InstanceFlowSummariesFilter;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Objects;

public class OnlyOneStatusFilterValidator implements ConstraintValidator<OnlyOneStatusFilter, InstanceFlowSummariesFilter> {

    @Override
    public boolean isValid(InstanceFlowSummariesFilter instanceFlowSummariesFilter, ConstraintValidatorContext constraintValidatorContext) {
        if (Objects.isNull(instanceFlowSummariesFilter)) {
            return true;
        }
        return !(Objects.nonNull(instanceFlowSummariesFilter.getStatuses())
                 && Objects.nonNull(instanceFlowSummariesFilter.getLatestStatusEvents()));
    }

}
