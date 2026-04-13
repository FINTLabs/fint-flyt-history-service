package no.novari.flyt.history.validation

import jakarta.validation.ConstraintViolation
import org.springframework.stereotype.Service
import org.springframework.validation.BindException

@Service
class ValidationErrorsFormattingService {
    fun <T> format(errors: Set<ConstraintViolation<T>>): String {
        val prefix = if (errors.size > 1) "Validation errors:" else "Validation error:"
        val formattedErrors =
            errors
                .asSequence()
                .map { violation ->
                    val propertyPath = violation.propertyPath.toString()
                    val propertyPrefix = if (propertyPath.isBlank()) "" else "$propertyPath "
                    "'$propertyPrefix${violation.message}'"
                }.sorted()
                .joinToString(prefix = "[", postfix = "]")

        return "$prefix $formattedErrors"
    }

    fun format(exception: BindException): String {
        val prefix = if (exception.fieldErrorCount > 1) "Validation errors:" else "Validation error:"
        val formattedErrors =
            exception.fieldErrors
                .asSequence()
                .map { error ->
                    "value '${error.rejectedValue}' for field '${error.field}' contains invalid invalid type or format"
                }.sorted()
                .joinToString(prefix = "[", postfix = "]")

        return "$prefix $formattedErrors"
    }
}
