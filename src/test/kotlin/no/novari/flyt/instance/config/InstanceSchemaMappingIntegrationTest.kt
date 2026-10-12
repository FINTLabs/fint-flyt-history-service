package no.novari.flyt.instance.config

import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import no.novari.flyt.history.Application
import no.novari.flyt.history.JpaAuditingTestConfig
import no.novari.flyt.history.model.event.EventType
import no.novari.flyt.history.repository.entities.EventEntity
import no.novari.flyt.history.repository.entities.InstanceFlowHeadersEmbeddable
import no.novari.flyt.instance.model.entities.InstanceObject
import no.novari.flyt.instance.model.entities.InstanceObjectCollection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.core.NestedExceptionUtils
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(showSql = false, properties = ["spring.jpa.hibernate.ddl-auto=validate"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [Application::class])
@Import(
    JpaAuditingTestConfig::class,
    InstanceDatabaseConfiguration::class,
    InstanceSchemaMappingIntegrationTest.EntitiesTestConfiguration::class,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InstanceSchemaMappingIntegrationTest {
    @Autowired
    lateinit var entityManager: EntityManager

    @Autowired
    lateinit var entityManagerFactory: EntityManagerFactory

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var dataSource: DataSource

    @Test
    fun `both entity domains are registered and instance schema is absent from search path`() {
        val entityTypes = entityManagerFactory.metamodel.entities.map { it.javaType }
        assertThat(
            entityTypes,
        ).contains(EventEntity::class.java, InstanceObject::class.java, InstanceObjectCollection::class.java)
        assertThat(jdbcTemplate.queryForObject("SHOW search_path", String::class.java)).isEqualTo(HISTORY_SCHEMA)
        assertThat(jdbcTemplate.queryForObject("SELECT current_schema()", String::class.java)).isEqualTo(HISTORY_SCHEMA)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT to_regclass('instance_object')::text",
                String::class.java,
            ),
        ).isNull()
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT to_regclass('$HISTORY_SCHEMA.instance_received_offset')::text",
                String::class.java,
            ),
        ).isNotNull()
    }

    @Test
    fun `instance graph and history event persist in their respective schemas`() {
        TransactionTemplate(transactionManager).executeWithoutResult { status ->
            val root = instanceGraph()
            val event = historyEvent()
            entityManager.persist(root)
            entityManager.persist(event)
            entityManager.flush()
            entityManager.clear()

            val loaded = entityManager.find(InstanceObject::class.java, root.id)
            assertThat(loaded.valuePerKey).containsEntry("rootKey", "rootValue")
            val child =
                loaded.objectCollectionPerKey
                    .getValue("children")
                    .objects
                    .single()
            assertThat(child.valuePerKey).containsEntry("childKey", "childValue")
            assertThat(loaded.createdAt).isNotNull()
            assertThat(child.createdAt).isNotNull()
            assertThat(entityManager.find(EventEntity::class.java, event.id).name).isEqualTo(event.name)
            assertThat(rowCount("$INSTANCE_SCHEMA.instance_object", root.id!!)).isEqualTo(1L)
            assertThat(rowCount("$INSTANCE_SCHEMA.instance_object", child.id!!)).isEqualTo(1L)
            assertThat(
                rowCount(
                    "$INSTANCE_SCHEMA.instance_object_collection",
                    loaded.objectCollectionPerKey.getValue("children").id!!,
                ),
            ).isEqualTo(1L)
            assertThat(
                jdbcTemplate.queryForObject(
                    "SELECT value FROM $INSTANCE_SCHEMA.instance_object_value_per_key WHERE instance_object_id = ? AND key = ?",
                    String::class.java,
                    root.id,
                    "rootKey",
                ),
            ).isEqualTo("rootValue")
            assertThat(rowCount("$HISTORY_SCHEMA.event", event.id)).isEqualTo(1L)
            assertThat(
                jdbcTemplate.queryForObject(
                    "SELECT file_id FROM $HISTORY_SCHEMA.file_id WHERE event_id = ?",
                    UUID::class.java,
                    event.id,
                ),
            ).isEqualTo(event.instanceFlowHeaders!!.fileIds.single())
            status.setRollbackOnly()
        }
    }

    @Test
    fun `one transaction rolls back writes in both schemas`() {
        val root = instanceGraph()
        val event = historyEvent()
        var collectionId = 0L
        TransactionTemplate(transactionManager).executeWithoutResult { status ->
            entityManager.persist(root)
            entityManager.persist(event)
            entityManager.flush()
            collectionId = root.objectCollectionPerKey.getValue("children").id!!
            assertThat(rowCount("$INSTANCE_SCHEMA.instance_object", root.id!!)).isEqualTo(1L)
            assertThat(rowCount("$HISTORY_SCHEMA.event", event.id)).isEqualTo(1L)
            status.setRollbackOnly()
        }
        assertThat(rowCount("$INSTANCE_SCHEMA.instance_object", root.id!!)).isZero()
        assertThat(rowCount("$INSTANCE_SCHEMA.instance_object_collection", collectionId)).isZero()
        assertThat(rowCount("$HISTORY_SCHEMA.event", event.id)).isZero()
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM $INSTANCE_SCHEMA.instance_object_value_per_key WHERE instance_object_id = ?",
                Long::class.java,
                root.id,
            )!!,
        ).isZero()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   "])
    fun `JPA startup fails with a clear error when instance schema is missing or blank`(schema: String) {
        val factory =
            LocalContainerEntityManagerFactoryBean().apply {
                setDataSource(this@InstanceSchemaMappingIntegrationTest.dataSource)
                setJpaVendorAdapter(HibernateJpaVendorAdapter())
                setPackagesToScan(InstanceObject::class.java.packageName)
                setJpaPropertyMap(
                    mapOf(
                        "hibernate.physical_naming_strategy" to
                            InstanceSchemaNamingStrategy(InstanceDatabaseProperties(schema)),
                        "hibernate.hbm2ddl.auto" to "validate",
                    ),
                )
            }
        val exception = assertThrows(Exception::class.java) { factory.afterPropertiesSet() }
        assertThat(NestedExceptionUtils.getMostSpecificCause(exception))
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("novari.flyt.instance.database.schema must be configured")
    }

    private fun rowCount(
        table: String,
        id: Long,
    ): Long = jdbcTemplate.queryForObject("SELECT count(*) FROM $table WHERE id = ?", Long::class.java, id)!!

    private fun instanceGraph() =
        InstanceObject(
            valuePerKey = mutableMapOf("rootKey" to "rootValue"),
            objectCollectionPerKey =
                mutableMapOf(
                    "children" to
                        InstanceObjectCollection(
                            objects =
                                mutableListOf(
                                    InstanceObject(valuePerKey = mutableMapOf("childKey" to "childValue")),
                                ),
                        ),
                ),
        )

    private fun historyEvent() =
        EventEntity(
            name = "instance-schema-mapping-test",
            timestamp = OffsetDateTime.now(),
            type = EventType.INFO,
            instanceFlowHeaders = InstanceFlowHeadersEmbeddable(fileIds = mutableListOf(UUID.randomUUID())),
        )

    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = [EventEntity::class, InstanceObject::class])
    class EntitiesTestConfiguration

    companion object {
        private const val HISTORY_SCHEMA = "mapping_test_history"
        private const val INSTANCE_SCHEMA = "mapping_test_instance"

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
