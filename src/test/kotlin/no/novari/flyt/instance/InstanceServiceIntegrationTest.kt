package no.novari.flyt.instance

import jakarta.persistence.EntityNotFoundException
import no.novari.flyt.history.Application
import no.novari.flyt.history.JpaAuditingTestConfig
import no.novari.flyt.instance.config.InstanceDatabaseConfiguration
import no.novari.flyt.instance.config.InstanceJpaConfiguration
import no.novari.flyt.instance.model.InstanceMappingService
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
import java.time.temporal.ChronoUnit
import java.util.Date

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(
    showSql = false,
    properties = [
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.enable_lazy_load_no_trans=false",
        "spring.jpa.open-in-view=false",
        "novari.flyt.instance-functionality.enabled=true",
    ],
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [Application::class])
@Import(
    JpaAuditingTestConfig::class,
    InstanceDatabaseConfiguration::class,
    InstanceJpaConfiguration::class,
    InstanceService::class,
    InstanceMappingService::class,
    InstanceServiceIntegrationTest.FixedClockConfiguration::class,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InstanceServiceIntegrationTest {
    @Autowired
    lateinit var instanceService: InstanceService

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    lateinit var applicationContext: ApplicationContext

    @AfterEach
    fun cleanUp() {
        TransactionTemplate(transactionManager).executeWithoutResult {
            jdbcTemplate.execute("DELETE FROM $INSTANCE_SCHEMA.instance_object_value_per_key")
            jdbcTemplate.execute("UPDATE $INSTANCE_SCHEMA.instance_object SET instance_object_collection_id = NULL")
            jdbcTemplate.execute("UPDATE $INSTANCE_SCHEMA.instance_object_collection SET instance_object_id = NULL")
            jdbcTemplate.execute("DELETE FROM $INSTANCE_SCHEMA.instance_object_collection")
            jdbcTemplate.execute("DELETE FROM $INSTANCE_SCHEMA.instance_object")
        }
    }

    @Test
    fun `enabled flag creates both services without Kafka collaborators`() {
        assertThat(applicationContext.getBeansOfType(InstanceService::class.java)).hasSize(1)
        assertThat(applicationContext.getBeansOfType(InstanceMappingService::class.java)).hasSize(1)
        assertThat(
            applicationContext
                .getBeansOfType(Clock::class.java)
                .values
                .single()
                .instant(),
        ).isEqualTo(NOW)
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
    }

    @Test
    fun `save and get return complete detached DTO graphs with generated identities and audit timestamps`() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
        val source = instanceGraph()
        val saved = instanceService.save(source)
        val loaded = instanceService.getById(saved.id!!)
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()

        val savedNodes = nodes(saved)
        val loadedNodes = nodes(loaded)
        assertThat(savedNodes).hasSize(3)
        assertThat(loadedNodes).hasSize(3)
        assertThat(savedNodes.map { it.id }).doesNotContainNull().doesNotHaveDuplicates()
        assertThat(savedNodes.map { it.id }).doesNotContainAnyElementsOf(nodes(source).map { it.id })
        assertThat(loaded.objectCollectionPerKey.getValue("empty")).isEmpty()
        savedNodes.zip(loadedNodes).forEach { (savedNode, loadedNode) ->
            assertThat(loadedNode.id).isEqualTo(savedNode.id)
            assertThat(loadedNode.valuePerKey).isEqualTo(savedNode.valuePerKey)
            assertThat(savedNode.createdAt).isNotNull().isNotEqualTo(Date(0))
            assertThat(loadedNode.createdAt!!.time).isCloseTo(savedNode.createdAt!!.time, within(1L))
        }
        assertThat(loadedNodes.map { it.valuePerKey.getValue("level") }).containsExactly("root", "child", "grandchild")
        assertThat(
            jdbcTemplate.queryForObject("SELECT count(*) FROM $INSTANCE_SCHEMA.instance_object", Long::class.java)!!,
        ).isEqualTo(3L)
    }

    @Test
    fun `saving a DTO with existing graph identities creates new instances without overwriting stored data`() {
        val original = instanceService.save(instanceGraph())
        val incoming = original.copy(valuePerKey = mutableMapOf("level" to "new root"))
        val saved = instanceService.save(incoming)
        assertThat(nodes(saved).map { it.id }).doesNotContainAnyElementsOf(nodes(original).map { it.id })
        assertThat(instanceService.getById(original.id!!).valuePerKey).containsEntry("level", "root")
        assertThat(instanceService.getById(saved.id!!).valuePerKey).containsEntry("level", "new root")
        assertThat(
            jdbcTemplate.queryForObject("SELECT count(*) FROM $INSTANCE_SCHEMA.instance_object", Long::class.java)!!,
        ).isEqualTo(6L)
    }

    @Test
    fun `getById propagates EntityNotFoundException for an unknown identity`() {
        assertThatThrownBy { instanceService.getById(-1L) }.isInstanceOf(EntityNotFoundException::class.java)
    }

    @Test
    fun `getAllOlderThan uses the injected clock and maps results before its transaction ends`() {
        val instances = listOf(instanceGraph(), InstanceObjectDto(), InstanceObjectDto()).map(instanceService::save)
        val threshold = NOW.minus(30, ChronoUnit.DAYS)
        jdbcTemplate.update(
            "UPDATE $INSTANCE_SCHEMA.instance_object SET created_at = ?",
            threshold.minusSeconds(1).atOffset(ZoneOffset.UTC),
        )
        instances
            .zip(listOf(threshold.minusSeconds(1), threshold, threshold.plusSeconds(1)))
            .forEach { (instance, createdAt) ->
                jdbcTemplate.update(
                    "UPDATE $INSTANCE_SCHEMA.instance_object SET created_at = ? WHERE id = ?",
                    createdAt.atOffset(ZoneOffset.UTC),
                    instance.id,
                )
            }

        val result = instanceService.getAllOlderThan(30)
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
        assertThat(result.map { it.id }).containsExactly(instances.first().id)
        assertThat(nodes(result.single()).map { it.valuePerKey.getValue("level") })
            .containsExactly("root", "child", "grandchild")
    }

    @ParameterizedTest
    @ValueSource(longs = [0, 1])
    fun `getAllOlderThan excludes old descendants of a root at or after the threshold`(rootOffset: Long) {
        val root = instanceService.save(instanceGraph())
        val threshold = NOW.minus(30, ChronoUnit.DAYS)
        jdbcTemplate.update(
            "UPDATE $INSTANCE_SCHEMA.instance_object SET created_at = ?",
            threshold.minusSeconds(1).atOffset(ZoneOffset.UTC),
        )
        jdbcTemplate.update(
            "UPDATE $INSTANCE_SCHEMA.instance_object SET created_at = ? WHERE id = ?",
            threshold.plusSeconds(rootOffset).atOffset(ZoneOffset.UTC),
            root.id,
        )

        assertThat(instanceService.getAllOlderThan(30)).isEmpty()
    }

    @Test
    fun `a database failure in a nested object rolls back the complete save`() {
        val invalidGrandchild = InstanceObjectDto(valuePerKey = mutableMapOf("x".repeat(256) to "too long key"))
        val child = InstanceObjectDto(objectCollectionPerKey = mutableMapOf("children" to listOf(invalidGrandchild)))
        val root =
            InstanceObjectDto(
                valuePerKey = mutableMapOf("valid" to "root"),
                objectCollectionPerKey = mutableMapOf("children" to listOf(child)),
            )
        assertThatThrownBy { instanceService.save(root) }.isInstanceOf(DataIntegrityViolationException::class.java)
        listOf("instance_object", "instance_object_collection", "instance_object_value_per_key").forEach { table ->
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM $INSTANCE_SCHEMA.$table", Long::class.java)!!)
                .isZero()
        }
    }

    private fun instanceGraph(): InstanceObjectDto {
        val grandchild =
            InstanceObjectDto(
                id = 900003L,
                valuePerKey = mutableMapOf("level" to "grandchild"),
                createdAt = Date(0),
            )
        val child =
            InstanceObjectDto(
                id = 900002L,
                valuePerKey = mutableMapOf("level" to "child"),
                objectCollectionPerKey = mutableMapOf("children" to listOf(grandchild)),
                createdAt = Date(0),
            )
        return InstanceObjectDto(
            id = 900001L,
            valuePerKey = mutableMapOf("level" to "root"),
            objectCollectionPerKey = mutableMapOf("children" to listOf(child), "empty" to emptyList()),
            createdAt = Date(0),
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
        private const val HISTORY_SCHEMA = "service_test_history"
        private const val INSTANCE_SCHEMA = "mapping_test_instance"
        private val NOW = Instant.parse("2026-10-20T12:00:00Z")

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
