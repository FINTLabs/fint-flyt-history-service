package no.fintlabs.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Constraint(validatedBy = OnlyOneStatusFilterValidator.class)
public @interface OnlyOneStatusFilter {

    String message() default "contains both latest status events and status";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
