package no.novari.flyt.history

import no.novari.flyt.history.mapping.InstanceFlowHeadersMappingService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.model.Error
import no.novari.flyt.kafka.model.ErrorCollection
import no.novari.flyt.kafka.model.InstanceErrorEvent
import no.novari.flyt.kafka.model.InstanceErrorOrigin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(showSql = false)
@Import(InstanceErrorEventService::class, InstanceFlowHeadersMappingService::class, JpaAuditingTestConfig::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InstanceErrorEventServiceTransactionTest {
    @Autowired
    lateinit var eventRepository: EventRepository

    @Autowired
    lateinit var instanceErrorEventService: InstanceErrorEventService

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        eventRepository.deleteAll()
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `both entries persist without an outer transaction`(newFormat: Boolean) {
        registerError(newFormat)

        assertThat(eventRepository.findAll()).hasSize(1)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `both entries join a rolling back outer transaction`(newFormat: Boolean) {
        TransactionTemplate(transactionManager).executeWithoutResult { status ->
            registerError(newFormat)
            status.setRollbackOnly()
        }

        assertThat(eventRepository.findAll()).isEmpty()
    }

    private fun registerError(newFormat: Boolean) {
        val headers =
            InstanceFlowHeaders
                .builder()
                .sourceApplicationId(1L)
                .sourceApplicationIntegrationId("sa-integration-1")
                .sourceApplicationInstanceId("sa-instance-1")
                .correlationId(UUID.randomUUID())
                .integrationId(100L)
                .build()
        val errors = ErrorCollection(listOf(Error("test-error", mapOf("key" to "value"))))
        val timestamp = Instant.parse("2026-01-15T10:20:30Z")

        if (newFormat) {
            instanceErrorEventService.registerError(
                headers,
                InstanceErrorEvent(name = InstanceErrorOrigin.REGISTRATION, errors = errors),
                timestamp,
                "source-app",
            )
        } else {
            instanceErrorEventService.registerError(
                headers,
                EventCategory.INSTANCE_REGISTRATION_ERROR,
                errors,
                timestamp,
                "source-app",
            )
        }
    }

    companion object {
        @JvmField
        @Container
        val postgreSQLContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:17")

        @JvmStatic
        @DynamicPropertySource
        fun postgreSQLProperties(registry: DynamicPropertyRegistry) {
            postgreSQLContainer.start()
            registry.add("fint.database.url", postgreSQLContainer::getJdbcUrl)
            registry.add("fint.database.username", postgreSQLContainer::getUsername)
            registry.add("fint.database.password", postgreSQLContainer::getPassword)
        }
    }
}
