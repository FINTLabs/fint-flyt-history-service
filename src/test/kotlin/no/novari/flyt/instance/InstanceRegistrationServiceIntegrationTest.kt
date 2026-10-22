package no.novari.flyt.instance

import jakarta.persistence.EntityManager
import no.novari.flyt.history.Application
import no.novari.flyt.history.InstanceInfoEventService
import no.novari.flyt.history.JpaAuditingTestConfig
import no.novari.flyt.history.mapping.InstanceFlowHeadersMappingService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.event.EventType
import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.instance.config.InstanceDatabaseConfiguration
import no.novari.flyt.instance.config.InstanceJpaConfiguration
import no.novari.flyt.instance.model.InstanceMappingService
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(
    showSql = false,
    properties = [
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.enable_lazy_load_no_trans=false",
        "spring.jpa.open-in-view=false",
        "novari.flyt.instance-functionality.enabled=true",
        "fint.application-id=registration-test-app",
    ],
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [Application::class])
@Import(
    JpaAuditingTestConfig::class,
    InstanceDatabaseConfiguration::class,
    InstanceJpaConfiguration::class,
    InstanceRegistrationService::class,
    InstanceService::class,
    InstanceMappingService::class,
    InstanceInfoEventService::class,
    InstanceFlowHeadersMappingService::class,
    InstanceRegistrationServiceIntegrationTest.FixedClockConfiguration::class,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InstanceRegistrationServiceIntegrationTest {
    @Autowired
    lateinit var registrationService: InstanceRegistrationService

    @Autowired
    lateinit var instanceService: InstanceService

    @Autowired
    lateinit var eventRepository: EventRepository

    @Autowired
    lateinit var headersMapper: InstanceFlowHeadersMappingService

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var entityManager: EntityManager

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    lateinit var applicationContext: ApplicationContext

    @AfterEach
    fun cleanUp() {
        TransactionTemplate(transactionManager).executeWithoutResult {
            eventRepository.deleteAll()
            jdbcTemplate.execute("DELETE FROM $INSTANCE_SCHEMA.instance_object_value_per_key")
            jdbcTemplate.execute("UPDATE $INSTANCE_SCHEMA.instance_object SET instance_object_collection_id = NULL")
            jdbcTemplate.execute("UPDATE $INSTANCE_SCHEMA.instance_object_collection SET instance_object_id = NULL")
            jdbcTemplate.execute("DELETE FROM $INSTANCE_SCHEMA.instance_object_collection")
            jdbcTemplate.execute("DELETE FROM $INSTANCE_SCHEMA.instance_object")
        }
    }

    @Test
    fun `registration commits the graph and one info event while preserving incoming headers`() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
        assertThat(applicationContext.getBeansOfType(InstanceRegistrationService::class.java)).hasSize(1)
        val headers = instanceFlowHeaders()
        val originalHeaders = headersMapper.toEmbeddable(headers)
        val saved = registrationService.register(headers, instanceGraph())
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()

        assertThat(saved.id).isNotNull().isNotEqualTo(headers.instanceId)
        assertThat(nodes(saved).map { it.id }).doesNotContainNull().doesNotHaveDuplicates()
        assertThat(nodes(saved).map { it.valuePerKey.getValue("level") })
            .containsExactly("root", "child", "grandchild")
        assertThat(saved.objectCollectionPerKey.getValue("empty")).isEmpty()
        assertThat(instanceService.getById(saved.id!!).valuePerKey).containsEntry("level", "root")
        assertThat(rowCount("$INSTANCE_SCHEMA.instance_object")).isEqualTo(3L)
        assertThat(rowCount("$HISTORY_SCHEMA.event")).isEqualTo(1L)
        assertThat(headersMapper.toEmbeddable(headers))
            .usingRecursiveComparison()
            .isEqualTo(originalHeaders)

        TransactionTemplate(transactionManager).executeWithoutResult {
            val event = eventRepository.findAll().single()
            val expectedHeaders = headersMapper.toEmbeddable(headers.toBuilder().instanceId(saved.id).build())
            assertThat(event.name).isEqualTo(EventCategory.INSTANCE_REGISTERED.eventName)
            assertThat(event.type).isEqualTo(EventType.INFO)
            assertThat(event.timestamp!!.toInstant()).isEqualTo(NOW)
            assertThat(event.applicationId).isEqualTo(APPLICATION_ID)
            assertThat(event.instanceFlowHeaders)
                .usingRecursiveComparison()
                .ignoringCollectionOrder()
                .isEqualTo(expectedHeaders)
        }
    }

    @Test
    fun `a nested instance database failure leaves no graph or registration history`() {
        val invalidChild = InstanceObjectDto(valuePerKey = mutableMapOf("x".repeat(256) to "too long key"))
        val root =
            InstanceObjectDto(
                valuePerKey = mutableMapOf("valid" to "root"),
                objectCollectionPerKey = mutableMapOf("children" to listOf(invalidChild)),
            )
        assertThatThrownBy { registrationService.register(instanceFlowHeaders(), root) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertNoRegistrationData()
    }

    @Test
    fun `a history database failure at commit rolls back the complete instance graph`() {
        jdbcTemplate.execute(
            "ALTER TABLE $HISTORY_SCHEMA.event ADD CONSTRAINT reject_registration_event " +
                "CHECK (name <> 'instance-registered')",
        )
        try {
            assertThatThrownBy { registrationService.register(instanceFlowHeaders(), instanceGraph()) }
                .isInstanceOf(DataIntegrityViolationException::class.java)
                .hasStackTraceContaining("reject_registration_event")
            assertNoRegistrationData()
        } finally {
            jdbcTemplate.execute("ALTER TABLE $HISTORY_SCHEMA.event DROP CONSTRAINT reject_registration_event")
        }
    }

    @Test
    fun `registration joins an outer transaction and both domains roll back together`() {
        TransactionTemplate(transactionManager).executeWithoutResult { status ->
            val saved = registrationService.register(instanceFlowHeaders(), instanceGraph())
            entityManager.flush()
            assertThat(rowCount("$INSTANCE_SCHEMA.instance_object")).isEqualTo(3L)
            assertThat(rowCount("$HISTORY_SCHEMA.event")).isEqualTo(1L)
            assertThat(
                eventRepository
                    .findAll()
                    .single()
                    .instanceFlowHeaders!!
                    .instanceId,
            ).isEqualTo(saved.id)
            status.setRollbackOnly()
        }
        assertNoRegistrationData()
    }

    private fun assertNoRegistrationData() {
        listOf("instance_object", "instance_object_collection", "instance_object_value_per_key").forEach { table ->
            assertThat(rowCount("$INSTANCE_SCHEMA.$table")).isZero()
        }
        assertThat(rowCount("$HISTORY_SCHEMA.event")).isZero()
        assertThat(rowCount("$HISTORY_SCHEMA.file_id")).isZero()
    }

    private fun rowCount(table: String): Long =
        jdbcTemplate.queryForObject("SELECT count(*) FROM $table", Long::class.java)!!

    private fun instanceFlowHeaders(): InstanceFlowHeaders =
        InstanceFlowHeaders
            .builder()
            .sourceApplicationId(1L)
            .sourceApplicationIntegrationId("integration-1")
            .sourceApplicationInstanceId("source-instance-1")
            .correlationId(UUID.fromString("53134ef2-4480-46d6-99a3-1920d36ea333"))
            .integrationId(100L)
            .configurationId(42L)
            .instanceId(900001L)
            .archiveInstanceId("archive-instance-1")
            .fileIds(listOf(UUID.fromString("16b1b2c3-5d6e-47f8-90ab-123456789abc"), UUID.randomUUID()))
            .build()

    private fun instanceGraph(): InstanceObjectDto {
        val grandchild = InstanceObjectDto(valuePerKey = mutableMapOf("level" to "grandchild"))
        val child =
            InstanceObjectDto(
                valuePerKey = mutableMapOf("level" to "child"),
                objectCollectionPerKey = mutableMapOf("children" to listOf(grandchild)),
            )
        return InstanceObjectDto(
            valuePerKey = mutableMapOf("level" to "root"),
            objectCollectionPerKey = mutableMapOf("children" to listOf(child), "empty" to emptyList()),
        )
    }

    private fun nodes(dto: InstanceObjectDto): List<InstanceObjectDto> =
        listOf(dto) +
            dto.objectCollectionPerKey.values
                .flatten()
                .flatMap(::nodes)

    @TestConfiguration(proxyBeanMethods = false)
    class FixedClockConfiguration {
        @Bean
        fun clock(): Clock = Clock.fixed(NOW, ZoneOffset.UTC)
    }

    companion object {
        private const val HISTORY_SCHEMA = "registration_test_history"
        private const val INSTANCE_SCHEMA = "mapping_test_instance"
        private const val APPLICATION_ID = "registration-test-app"
        private val NOW = Instant.parse("2026-10-21T10:20:30Z")

        @JvmField
        @Container
        val postgreSQLContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:17").withInitScript("db/instance-fixture/init.sql")

        @JvmStatic
        @DynamicPropertySource
        fun postgreSQLProperties(registry: DynamicPropertyRegistry) {
            postgreSQLContainer.start()
            registry.add("fint.database.url", postgreSQLContainer::getJdbcUrl)
            registry.add("fint.database.username", postgreSQLContainer::getUsername)
            registry.add("fint.database.password", postgreSQLContainer::getPassword)
            registry.add("spring.datasource.hikari.schema") { HISTORY_SCHEMA }
            registry.add("novari.flyt.instance.database.schema") { INSTANCE_SCHEMA }
        }
    }
}
