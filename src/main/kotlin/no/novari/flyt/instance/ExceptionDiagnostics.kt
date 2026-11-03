package no.novari.flyt.instance

import org.hibernate.exception.ConstraintViolationException
import java.sql.SQLException

internal fun safeDiagnosticCause(failure: Throwable): RuntimeException {
    val description =
        buildString {
            append(failure.javaClass.name)
            if (failure is SQLException) failure.sqlState?.let { append(" SQLSTATE=$it") }
            if (failure is ConstraintViolationException) {
                val constraintName: String? = failure.constraintName
                if (constraintName != null) append(" constraint=$constraintName")
            }
        }
    return RuntimeException(description, failure.cause?.let(::safeDiagnosticCause)).apply {
        stackTrace = failure.stackTrace
        failure.suppressed.forEach { addSuppressed(safeDiagnosticCause(it)) }
    }
}
