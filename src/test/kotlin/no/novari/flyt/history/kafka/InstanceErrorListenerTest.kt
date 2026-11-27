package no.novari.flyt.history.kafka

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.history.InstanceErrorEventService
import no.novari.flyt.history.InstanceInfoEventService
import no.novari.flyt.history.JpaAuditingTestConfig
import no.novari.flyt.history.mapping.InstanceFlowHeadersMappingService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.event.EventType
import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.instance.InstanceDeletionService
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.model.ErrorCollection
import no.novari.flyt.kafka.model.InstanceErrorEvent
import no.novari.flyt.kafka.model.InstanceErrorOrigin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.unit.DataSize
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.UUID

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(showSql = false)
@Import(InstanceErrorEventService::class, InstanceFlowHeadersMappingService::class, JpaAuditingTestConfig::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InstanceErrorListenerTest {
    @Autowired
    lateinit var eventRepository: EventRepository

    @Autowired
    lateinit var instanceErrorEventService: InstanceErrorEventService

    private lateinit var listener: HistoryEventListener

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        eventRepository.deleteAll()
        listener =
            HistoryEventListener(
                mock(),
                mock<InstanceInfoEventService>(),
                instanceErrorEventService,
                mock<InstanceDeletionService>(),
            )
    }

    @ParameterizedTest
    @EnumSource(InstanceErrorOrigin::class)
    fun `listener persists EventEntity with correct name for each InstanceErrorOrigin`(origin: InstanceErrorOrigin) {
        val expectedCategory = expectedCategory(origin)
        val errors =
            ErrorCollection(
                listOf(
                    no.novari.flyt.kafka.model
                        .Error("test-error", mapOf("key" to "val")),
                ),
            )
        val event = InstanceErrorEvent(name = origin, errors = errors)

        listener.onInstanceError(event, headers(), 0L, "test-app".toByteArray())

        val saved = eventRepository.findAll()
        assertThat(saved).hasSize(1)
        val entity = saved.single()
        assertThat(entity.name).isEqualTo(expectedCategory.eventName)
        assertThat(entity.type).isEqualTo(EventType.ERROR)
        assertThat(entity.createdBy).isEqualTo(Actor.System)
        assertThat(entity.errors).hasSize(1)
        assertThat(entity.errors.first().errorCode).isEqualTo("test-error")
    }

    private fun expectedCategory(origin: InstanceErrorOrigin): EventCategory =
        when (origin) {
            InstanceErrorOrigin.RECEIVAL -> EventCategory.INSTANCE_RECEIVAL_ERROR
            InstanceErrorOrigin.REGISTRATION -> EventCategory.INSTANCE_REGISTRATION_ERROR
            InstanceErrorOrigin.RETRY_REQUEST -> EventCategory.INSTANCE_RETRY_REQUEST_ERROR
            InstanceErrorOrigin.MAPPING -> EventCategory.INSTANCE_MAPPING_ERROR
            InstanceErrorOrigin.DISPATCHING -> EventCategory.INSTANCE_DISPATCHING_ERROR
        }

    private fun headers() =
        InstanceFlowHeaders(
            sourceApplicationId = 1L,
            sourceApplicationIntegrationId = "sa-integration-1",
            sourceApplicationInstanceId = "sa-instance-1",
            correlationId = UUID.randomUUID(),
            integrationId = 100L,
        )

    companion object {
        @JvmField
        @Container
        val postgreSQLContainer: PostgreSQLContainer =
            PostgreSQLContainer("postgres:17")
                .withUrlParam("reWriteBatchedInserts", "true")
                .withCreateContainerCmdModifier { createContainerCmd ->
                    requireNotNull(createContainerCmd.hostConfig)
                        .withCpuCount(2L)
                        .withMemory(DataSize.ofGigabytes(8).toBytes())
                }

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
