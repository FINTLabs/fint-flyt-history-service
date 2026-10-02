package no.novari.flyt.history

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorContext
import no.novari.flyt.history.mapping.InstanceFlowHeadersMappingService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
@Import(InstanceInfoEventService::class, InstanceFlowHeadersMappingService::class, JpaAuditingTestConfig::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InstanceInfoEventServiceTransactionTest {
    @Autowired
    lateinit var eventRepository: EventRepository

    @Autowired
    lateinit var instanceInfoEventService: InstanceInfoEventService

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    private val actor = Actor.User(UUID.fromString("53134ef2-4480-46d6-99a3-1920d36ea333"))
    private val timestamp = Instant.parse("2026-01-15T10:20:30Z")

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        eventRepository.deleteAll()
    }

    @Test
    fun `retry event persists explicit actor without an outer transaction`() {
        registerRetryEvent()

        assertThat(eventRepository.findAll().single().createdBy).isEqualTo(actor)
        assertThat(ActorContext.currentActor()).isNull()
    }

    @Test
    fun `retry event keeps actor and rolls back with outer transaction`() {
        TransactionTemplate(transactionManager).executeWithoutResult { status ->
            registerRetryEvent()
            assertThat(eventRepository.findAll().single().createdBy).isEqualTo(actor)
            status.setRollbackOnly()
        }

        assertThat(eventRepository.findAll()).isEmpty()
        assertThat(ActorContext.currentActor()).isNull()
    }

    private fun registerRetryEvent() {
        instanceInfoEventService.registerEvent(
            InstanceFlowHeaders
                .builder()
                .sourceApplicationId(1L)
                .sourceApplicationIntegrationId("sa-integration-1")
                .sourceApplicationInstanceId("sa-instance-1")
                .correlationId(UUID.randomUUID())
                .integrationId(100L)
                .build(),
            EventCategory.INSTANCE_REQUESTED_FOR_RETRY,
            timestamp,
            "source-app",
            actor,
        )
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
