package no.fintlabs.validation;

import org.springframework.stereotype.Service;
import org.springframework.web.bind.support.WebExchangeBindException;

import jakarta.validation.ConstraintViolation;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ValidationErrorsFormattingService {

    public <T> String format(Set<ConstraintViolation<T>> errors) {
        return "Validation error" + (errors.size() > 1 ? "s:" : ":") + " " +
               errors
                       .stream()
                       .map(constraintViolation -> "'" + (
                                       constraintViolation.getPropertyPath().toString().isBlank()
                                               ? ""
                                               : constraintViolation.getPropertyPath().toString() + " "
                               ) + constraintViolation.getMessage() + "'"
                       )
                       .sorted(String::compareTo)
                       .collect(Collectors.joining(", ", "[", "]"));
    }

    public <T> String format(WebExchangeBindException e) {
        return e.getReason() + (e.getFieldErrorCount() > 1 ? "s:" : ":") + " " +
               e.getFieldErrors()
                       .stream()
                       .map(error -> "value '" + error.getRejectedValue() +
                                     "' for field '" + error.getField() +
                                     "' contains invalid invalid type or format")
                       .sorted(String::compareTo)
                       .collect(Collectors.joining(", ", "[", "]"));
    }

}
