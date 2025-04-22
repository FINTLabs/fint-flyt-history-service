package no.fintlabs.validation;

import no.fintlabs.model.instance.InstanceFlowSummariesFilter;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
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
