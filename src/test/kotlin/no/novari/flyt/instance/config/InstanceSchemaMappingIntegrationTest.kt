package no.novari.flyt.instance.config

import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import no.novari.flyt.history.Application
import no.novari.flyt.history.JpaAuditingTestConfig
import no.novari.flyt.history.model.event.EventType
import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.history.repository.entities.EventEntity
import no.novari.flyt.history.repository.entities.InstanceFlowHeadersEmbeddable
import no.novari.flyt.instance.InstanceRepository
import no.novari.flyt.instance.model.entities.InstanceObject
import no.novari.flyt.instance.model.entities.InstanceObjectCollection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.core.NestedExceptionUtils
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.orm.jpa.JpaTransactionManager
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
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(
    showSql = false,
    properties = ["spring.jpa.hibernate.ddl-auto=validate", "novari.flyt.instance-functionality.enabled=true"],
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [Application::class])
@Import(JpaAuditingTestConfig::class, InstanceDatabaseConfiguration::class, InstanceJpaConfiguration::class)
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
    lateinit var applicationContext: ApplicationContext

    @Autowired
    lateinit var instanceRepository: InstanceRepository

    @Autowired
    lateinit var eventRepository: EventRepository

    @Test
    fun `enabled flag registers both domains and repositories with one persistence context`() {
        val entityTypes = entityManagerFactory.metamodel.entities.map { it.javaType }
        assertThat(
            entityTypes,
        ).contains(EventEntity::class.java, InstanceObject::class.java, InstanceObjectCollection::class.java)
        assertThat(
            applicationContext.getBeansOfType(EntityManagerFactory::class.java),
        ).containsOnlyKeys("entityManagerFactory")
        assertThat(
            applicationContext.getBeansOfType(PlatformTransactionManager::class.java),
        ).containsOnlyKeys("transactionManager")
        assertThat((transactionManager as JpaTransactionManager).entityManagerFactory).isSameAs(entityManagerFactory)
        assertThat(eventRepository.count()).isZero()
        assertThat(instanceRepository.count()).isZero()
        assertThat(jdbcTemplate.queryForObject("SHOW search_path", String::class.java)).isEqualTo(HISTORY_SCHEMA)
        assertThat(jdbcTemplate.queryForObject("SELECT current_schema()", String::class.java)).isEqualTo(HISTORY_SCHEMA)
        assertThat(
            jdbcTemplate.queryForObject("SELECT to_regclass('instance_object')::text", String::class.java),
        ).isNull()
        assertThat(
            jdbcTemplate.queryForObject<String>(
                "SELECT to_regclass('$HISTORY_SCHEMA.instance_received_offset')::text",
            ),
        ).isNotNull()
    }

    @Test
    fun `repositories persist and load instance graph and history event in their respective schemas`() {
        inRollbackTransaction {
            val root = instanceRepository.saveAndFlush(instanceGraph())
            val event = eventRepository.saveAndFlush(historyEvent())
            entityManager.clear()

            val loaded = instanceRepository.findById(root.id!!).orElseThrow()
            assertThat(loaded.valuePerKey).containsEntry("rootKey", "rootValue")
            val child =
                loaded.objectCollectionPerKey
                    .getValue("children")
                    .objects
                    .single()
            assertThat(child.valuePerKey).containsEntry("childKey", "childValue")
            assertThat(loaded.createdAt).isNotNull()
            assertThat(child.createdAt).isNotNull()
            assertThat(eventRepository.findById(event.id).orElseThrow().name).isEqualTo(event.name)
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
                    "SELECT value FROM $INSTANCE_SCHEMA.instance_object_value_per_key " +
                        "WHERE instance_object_id = ? AND key = ?",
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
            assertThat(
                eventRepository.findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
                    1L,
                    "integration-1",
                    "source-instance-1",
                ),
            ).containsExactly("archive-instance-1")
        }
    }

    @Test
    fun `repository updates values and removes orphaned children and collections`() {
        inRollbackTransaction {
            val root = instanceRepository.saveAndFlush(instanceGraph())
            val collection = root.objectCollectionPerKey.getValue("children")
            val childId = collection.objects.single().id!!
            val collectionId = collection.id!!
            root.valuePerKey["rootKey"] = "updatedValue"
            collection.objects.clear()
            instanceRepository.saveAndFlush(root)
            entityManager.clear()

            val updated = instanceRepository.findById(root.id!!).orElseThrow()
            assertThat(updated.valuePerKey).containsEntry("rootKey", "updatedValue")
            assertThat(updated.objectCollectionPerKey.getValue("children").objects).isEmpty()
            assertThat(rowCount("$INSTANCE_SCHEMA.instance_object", childId)).isZero()
            assertThat(valueCount(childId)).isZero()
            updated.objectCollectionPerKey.clear()
            instanceRepository.saveAndFlush(updated)
            entityManager.clear()

            assertThat(instanceRepository.findById(root.id!!).orElseThrow().objectCollectionPerKey).isEmpty()
            assertThat(rowCount("$INSTANCE_SCHEMA.instance_object_collection", collectionId)).isZero()
        }
    }

    @Test
    fun `repository deletion cascades to the complete instance graph`() {
        inRollbackTransaction {
            val root = instanceRepository.saveAndFlush(instanceGraph())
            val collection = root.objectCollectionPerKey.getValue("children")
            val childId = collection.objects.single().id!!
            val collectionId = collection.id!!
            entityManager.clear()
            instanceRepository.deleteById(root.id!!)
            instanceRepository.flush()
            entityManager.clear()

            assertThat(instanceRepository.findById(root.id!!)).isEmpty()
            assertThat(rowCount("$INSTANCE_SCHEMA.instance_object", childId)).isZero()
            assertThat(rowCount("$INSTANCE_SCHEMA.instance_object_collection", collectionId)).isZero()
            assertThat(valueCount(root.id!!)).isZero()
            assertThat(valueCount(childId)).isZero()
        }
    }

    @Test
    fun `findAllOlderThan excludes instances at and after the threshold`() {
        inRollbackTransaction {
            val threshold = Instant.parse("2026-01-01T12:00:00Z")
            val instances = listOf(InstanceObject(), InstanceObject(), InstanceObject())
            val timestamps = listOf(threshold.minusSeconds(1), threshold, threshold.plusSeconds(1))
            instances.zip(timestamps).forEach { (instance, createdAt) ->
                instanceRepository.saveAndFlush(instance)
                jdbcTemplate.update(
                    "UPDATE $INSTANCE_SCHEMA.instance_object SET created_at = ? WHERE id = ?",
                    createdAt.atOffset(ZoneOffset.UTC),
                    instance.id,
                )
            }
            entityManager.clear()
            assertThat(
                instanceRepository.findAllOlderThan(threshold).map { it.id },
            ).containsExactly(instances.first().id)
        }
    }

    @ParameterizedTest
    @ValueSource(longs = [-1, 0, 1])
    fun `only old root instances are returned when descendants are old`(rootOffset: Long) {
        inRollbackTransaction {
            val threshold = Instant.parse("2026-01-01T12:00:00Z")
            val root = instanceRepository.saveAndFlush(instanceGraph())
            val childId =
                root.objectCollectionPerKey
                    .getValue("children")
                    .objects
                    .single()
                    .id!!
            jdbcTemplate.update(
                "UPDATE $INSTANCE_SCHEMA.instance_object SET created_at = ? WHERE id IN (?, ?)",
                threshold.minusSeconds(1).atOffset(ZoneOffset.UTC),
                root.id,
                childId,
            )
            jdbcTemplate.update(
                "UPDATE $INSTANCE_SCHEMA.instance_object SET created_at = ? WHERE id = ?",
                threshold.plusSeconds(rootOffset).atOffset(ZoneOffset.UTC),
                root.id,
            )
            entityManager.clear()

            val result = instanceRepository.findAllOlderThan(threshold)
            val expectedIds = if (rootOffset < 0) listOf(root.id) else emptyList()
            assertThat(result.map { it.id }).containsExactlyElementsOf(expectedIds)
            if (result.isNotEmpty()) {
                assertThat(
                    result
                        .single()
                        .objectCollectionPerKey
                        .getValue("children")
                        .objects
                        .single()
                        .id,
                ).isEqualTo(childId)
            }
        }
    }

    @Test
    fun `one transaction rolls back writes through both repositories`() {
        val root = instanceGraph()
        val event = historyEvent()
        var collectionId = 0L
        var childId = 0L
        inRollbackTransaction {
            instanceRepository.saveAndFlush(root)
            eventRepository.saveAndFlush(event)
            val collection = root.objectCollectionPerKey.getValue("children")
            collectionId = collection.id!!
            childId = collection.objects.single().id!!
            assertThat(rowCount("$INSTANCE_SCHEMA.instance_object", root.id!!)).isEqualTo(1L)
            assertThat(rowCount("$HISTORY_SCHEMA.event", event.id)).isEqualTo(1L)
        }
        assertThat(rowCount("$INSTANCE_SCHEMA.instance_object", root.id!!)).isZero()
        assertThat(rowCount("$INSTANCE_SCHEMA.instance_object", childId)).isZero()
        assertThat(rowCount("$INSTANCE_SCHEMA.instance_object_collection", collectionId)).isZero()
        assertThat(rowCount("$HISTORY_SCHEMA.event", event.id)).isZero()
        assertThat(valueCount(root.id!!)).isZero()
        assertThat(valueCount(childId)).isZero()
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["", "   "])
    fun `enabled flag fails startup when instance schema is missing or blank`(schema: String?) {
        var runner =
            instanceJpaContextRunner(postgreSQLContainer, HISTORY_SCHEMA)
                .withPropertyValues("novari.flyt.instance-functionality.enabled=true")
        if (schema != null) {
            runner = runner.withPropertyValues("novari.flyt.instance.database.schema=$schema")
        }
        runner.run { context ->
            assertThat(context).hasFailed()
            assertThat(NestedExceptionUtils.getMostSpecificCause(context.startupFailure!!))
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("novari.flyt.instance.database.schema must be configured")
        }
    }

    private fun inRollbackTransaction(block: () -> Unit) {
        TransactionTemplate(transactionManager).executeWithoutResult { status ->
            block()
            status.setRollbackOnly()
        }
    }

    private fun rowCount(
        table: String,
        id: Long,
    ): Long = jdbcTemplate.queryForObject("SELECT count(*) FROM $table WHERE id = ?", Long::class.java, id)!!

    private fun valueCount(instanceId: Long): Long =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM $INSTANCE_SCHEMA.instance_object_value_per_key WHERE instance_object_id = ?",
            Long::class.java,
            instanceId,
        )!!

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
            instanceFlowHeaders =
                InstanceFlowHeadersEmbeddable(
                    sourceApplicationId = 1L,
                    sourceApplicationIntegrationId = "integration-1",
                    sourceApplicationInstanceId = "source-instance-1",
                    archiveInstanceId = "archive-instance-1",
                    fileIds = mutableListOf(UUID.randomUUID()),
                ),
        )

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
