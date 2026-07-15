package no.novari.flyt.history

import no.novari.flyt.history.exceptions.LatestStatusEventNotOfTypeErrorException
import no.novari.flyt.history.exceptions.NoPreviousStatusEventsFoundException
import no.novari.flyt.history.exceptions.RequestValidationException
import no.novari.flyt.history.validation.ValidationErrorsFormattingService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class GlobalExceptionHandler(
    private val validationErrorsFormattingService: ValidationErrorsFormattingService,
) {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(RequestValidationException::class)
    fun handleRequestValidation(exception: RequestValidationException): ProblemDetail {
        logger.warn("Request validation failed", exception)
        return createProblemDetail(
            status = HttpStatus.UNPROCESSABLE_ENTITY,
            title = "Unprocessable Entity",
            detail = exception.formattedMessage,
        )
    }

    @ExceptionHandler(BindException::class)
    fun handleBindException(exception: BindException): ProblemDetail {
        logger.warn("Request binding validation failed", exception)
        return createProblemDetail(
            status = HttpStatus.UNPROCESSABLE_ENTITY,
            title = "Unprocessable Entity",
            detail = validationErrorsFormattingService.format(exception),
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(exception: HttpMessageNotReadableException): ProblemDetail {
        logger.warn("Malformed request body", exception)
        return createProblemDetail(
            status = HttpStatus.UNPROCESSABLE_ENTITY,
            title = "Unprocessable Entity",
            detail = "Validation error: malformed request body",
        )
    }

    @ExceptionHandler(NoPreviousStatusEventsFoundException::class)
    fun handleNoPreviousStatusEventsFound(exception: NoPreviousStatusEventsFoundException): ProblemDetail {
        logger.warn("No previous status event found", exception)
        return createProblemDetail(
            status = HttpStatus.NOT_FOUND,
            title = "Not Found",
            detail = "No previous event found",
        )
    }

    @ExceptionHandler(LatestStatusEventNotOfTypeErrorException::class)
    fun handleLatestStatusEventNotOfTypeError(exception: LatestStatusEventNotOfTypeErrorException): ProblemDetail {
        logger.warn("Latest status event is not of type error", exception)
        return createProblemDetail(
            status = HttpStatus.BAD_REQUEST,
            title = "Bad Request",
            detail = "Previous event status is not of type ERROR",
        )
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatch(exception: MethodArgumentTypeMismatchException): ProblemDetail {
        logger.warn("Request parameter type mismatch", exception)
        return createProblemDetail(
            status = HttpStatus.BAD_REQUEST,
            title = "Bad Request",
            detail = "Invalid value for request parameter '${exception.name}'",
        )
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(exception: ResponseStatusException): ProblemDetail {
        logger.warn("Response status exception with status={}", exception.statusCode, exception)
        return exception.reason
            ?.let { ProblemDetail.forStatusAndDetail(exception.statusCode, it) }
            ?: ProblemDetail.forStatus(exception.statusCode)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnhandledException(exception: Exception): ProblemDetail {
        logger.error("Unhandled exception", exception)
        return createProblemDetail(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            title = "Internal Server Error",
            detail = "An unexpected error occurred",
        )
    }

    private fun createProblemDetail(
        status: HttpStatus,
        title: String,
        detail: String,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            this.title = title
        }
}
