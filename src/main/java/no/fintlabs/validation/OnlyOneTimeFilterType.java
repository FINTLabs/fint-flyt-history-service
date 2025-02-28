package no.fintlabs.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Constraint(validatedBy = OnlyOneTimeFilterTypeValidator.class)
public @interface OnlyOneTimeFilterType {

    String message() default "contains multiple time filters";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
