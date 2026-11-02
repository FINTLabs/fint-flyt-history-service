package no.novari.flyt.instance.kafka

import org.assertj.core.api.Assertions.assertThat
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.CannotGetJdbcConnectionException
import java.sql.SQLException

class InstanceReceivedProcessingExceptionTest {
    @Test
    fun `diagnostics retain SQL state constraint name types and frames without raw error messages`() {
        val sql = SQLException("secret-failing-row", "23514")
        val constraint = ConstraintViolationException("secret-SQL-with-values", sql, "instance_key_check")
        val original = DataIntegrityViolationException("secret-instance-data", constraint)

        val failure = InstanceReceivedProcessingException("Registration failed", original, false)
        val diagnostic = failure.stackTraceToString()

        assertThat(diagnostic)
            .contains(
                DataIntegrityViolationException::class.java.name,
                ConstraintViolationException::class.java.name,
                SQLException::class.java.name,
                "SQLSTATE=23514",
                "constraint=instance_key_check",
            ).doesNotContain("secret")
        assertThat(failure.cause).isNotSameAs(original)
        assertThat(failure.cause!!.stackTrace).containsExactly(*original.stackTrace)
        assertThat(
            failure.cause!!
                .cause!!
                .cause!!
                .stackTrace,
        ).containsExactly(*sql.stackTrace)
    }

    @Test
    fun `retryable connection failures preserve technical cause details without connection messages`() {
        val sql = SQLException("secret-connection-parameters", "08006")
        val original = CannotGetJdbcConnectionException("secret-database-details", sql)

        val failure = InstanceReceivedProcessingException("Registration failed", original, true)

        assertThat(failure.retryable).isTrue()
        assertThat(failure.stackTraceToString())
            .contains(CannotGetJdbcConnectionException::class.java.name, "SQLSTATE=08006")
            .doesNotContain("secret")
    }
}
