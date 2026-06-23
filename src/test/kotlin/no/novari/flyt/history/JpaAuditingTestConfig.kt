package no.novari.flyt.history

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@TestConfiguration
@EnableJpaAuditing(auditorAwareRef = "flytAuditorAware")
class JpaAuditingTestConfig
