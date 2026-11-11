package no.novari.flyt.history

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional

@TestConfiguration
@EnableJpaAuditing(auditorAwareRef = "flytAuditorAware", dateTimeProviderRef = "microsecondDateTimeProvider")
class JpaAuditingTestConfig {
    // PostgreSQL rounds to microseconds, so a finer in-memory timestamp can round into the next millisecond and
    // differ from the stored value once mapped to Date.
    @Bean
    fun microsecondDateTimeProvider() = DateTimeProvider { Optional.of(Instant.now().truncatedTo(ChronoUnit.MICROS)) }
}
