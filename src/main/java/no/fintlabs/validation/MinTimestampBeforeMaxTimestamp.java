package no.fintlabs.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Constraint(validatedBy = MinTimestampBeforeMaxTimestampValidator.class)
public @interface MinTimestampBeforeMaxTimestamp {

    String message() default "timestampMin must be before timestampMax";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
