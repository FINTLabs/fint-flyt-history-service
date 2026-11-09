package no.novari.flyt.instance.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import no.novari.flyt.history.Application
import no.novari.flyt.history.ClockConfiguration
import no.novari.flyt.history.InstanceErrorEventService
import no.novari.flyt.history.JpaAuditingTestConfig
import no.novari.flyt.history.mapping.InstanceFlowHeadersMappingService
import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.instance.InstanceReceivedOffsetRepository
import no.novari.flyt.instance.InstanceReceivedPositions
import no.novari.flyt.instance.InstanceRegistrationService
import no.novari.flyt.instance.InstanceService
import no.novari.flyt.instance.config.InstanceDatabaseConfiguration
import no.novari.flyt.instance.config.InstanceJpaConfiguration
import no.novari.flyt.instance.model.InstanceMappingService
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeadersMapper
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.admin.OffsetSpec
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doCallRealMethod
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(
    showSql = false,
    properties = [
        "novari.flyt.instance-functionality.enabled=true",
        "novari.kafka.topic.org-id=$ORG_ID",
        "novari.kafka.default-replicas=1",
        "fint.application-id=consumer-test",
    ],
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [Application::class])
@ImportAutoConfiguration(JacksonAutoConfiguration::class, KafkaAutoConfiguration::class)
@Import(
    JpaAuditingTestConfig::class,
    ClockConfiguration::class,
    InstanceDatabaseConfiguration::class,
    InstanceJpaConfiguration::class,
    InstanceService::class,
    InstanceMappingService::class,
    InstanceErrorEventService::class,
    InstanceFlowHeadersMappingService::class,
    InstanceReceivedOffsetRepository::class,
    InstanceReceivedPositions::class,
    InstanceRegistrationService::class,
    InstanceRegisteredEventProducerService::class,
    InstanceReceivedEventConsumerConfiguration::class,
    InstanceReceivedEventConsumerIntegrationTest.KafkaLibraries::class,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InstanceReceivedEventConsumerIntegrationTest {
    @Autowired lateinit var consumer: ConcurrentMessageListenerContainer<String, InstanceObjectDto>

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var events: EventRepository

    @Autowired lateinit var objectMapper: ObjectMapper

    @Autowired lateinit var headersMapper: InstanceFlowHeadersMapper

    @MockitoSpyBean lateinit var producerService: InstanceRegisteredEventProducerService

    @MockitoSpyBean lateinit var offsetRepository: InstanceReceivedOffsetRepository

    private lateinit var admin: Admin
    private lateinit var producer: KafkaProducer<String, ByteArray>
    private var start = 0L
    private var registeredBefore = 0L

    @BeforeEach
    fun setUp() {
        consumer.stop()
        admin = Admin.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers))
        producer =
            KafkaProducer(
                mapOf(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java,
                ),
            )
        start = endOffset(RECEIVED)
        registeredBefore = endOffset(REGISTERED)
    }

    @AfterEach
    fun cleanUp() {
        consumer.stop()
        producer.close()
        admin.close()
        events.deleteAll()
        jdbc.execute("DELETE FROM instance_received_offset")
        jdbc.execute("DELETE FROM $INSTANCE_SCHEMA.instance_object_value_per_key")
        jdbc.execute("DELETE FROM $INSTANCE_SCHEMA.instance_object")
    }

    @Test
    fun `starts at the database position and registers each record once across restarts`() {
        send(instance("before position"))
        send(instance("first"))
        insertPosition(start + 1)

        consumer.start()
        awaitPosition(start + 2)
        consumer.stop()
        consumer.start()
        send(instance("second"))
        awaitPosition(start + 3)

        assertThat(labels()).containsExactlyInAnyOrder("first", "second")
        assertThat(endOffset(REGISTERED) - registeredBefore).isEqualTo(2)
    }

    @Test
    fun `invalid input is recorded once as a registration error and the position moves on`() {
        send(InstanceObjectDto(valuePerKey = mutableMapOf("x".repeat(256) to "invalid")))
        insertPosition(start)

        consumer.start()
        awaitPosition(start + 1)

        assertThat(events.findAll().map { it.name }).containsExactly("instance-registration-error")
        assertThat(labels()).isEmpty()
        assertThat(endOffset(REGISTERED)).isEqualTo(registeredBefore)
    }

    @Test
    fun `without a position row nothing is saved or published until the row exists`() {
        send(instance("waiting"))

        consumer.start()
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).until { labels().isEmpty() }
        assertThat(endOffset(REGISTERED)).isEqualTo(registeredBefore)

        insertPosition(start)
        awaitPosition(start + 1)
        assertThat(labels()).containsExactly("waiting")
    }

    @Test
    fun `a failed send is retried with the instance already saved and publishes once`() {
        doThrow(IllegalStateException("broker unavailable"))
            .doCallRealMethod()
            .whenever(producerService)
            .publish(any(), any())
        send(instance("retried"))
        insertPosition(start)

        consumer.start()
        awaitPosition(start + 1)

        assertThat(labels()).containsExactly("retried")
        assertThat(endOffset(REGISTERED) - registeredBefore).isEqualTo(1)
    }

    @Test
    fun `a failed position write after the send does not publish again`() {
        doThrow(IllegalStateException("database unavailable"))
            .doCallRealMethod()
            .whenever(offsetRepository)
            .markProcessed(any(), any(), any())
        send(instance("published once"))
        insertPosition(start)

        consumer.start()
        awaitPosition(start + 1)

        assertThat(labels()).containsExactly("published once")
        assertThat(endOffset(REGISTERED) - registeredBefore).isEqualTo(1)
    }

    @Test
    fun `a committed Kafka offset ahead of the database position never skips records`() {
        send(instance("first"))
        send(instance("second"))
        admin
            .alterConsumerGroupOffsets(GROUP, mapOf(TopicPartition(RECEIVED, 0) to OffsetAndMetadata(start + 1)))
            .all()
            .get(20, TimeUnit.SECONDS)

        consumer.start()
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).until { labels().isEmpty() }
        insertPosition(start)
        awaitPosition(start + 2)

        assertThat(labels()).containsExactlyInAnyOrder("first", "second")
        assertThat(endOffset(REGISTERED) - registeredBefore).isEqualTo(2)
    }

    @Test
    fun `a record without usable headers is skipped without history and does not block the next`() {
        send(objectMapper.writeValueAsBytes(instance("no headers")), headers = null)
        send(instance("next"))
        insertPosition(start)

        consumer.start()
        awaitPosition(start + 2)

        assertThat(labels()).containsExactly("next")
        assertThat(events.findAll()).isEmpty()
        assertThat(endOffset(REGISTERED) - registeredBefore).isEqualTo(1)
    }

    @Test
    fun `a missing payload is recorded as a registration error and does not block the next`() {
        send(null, headers())
        send(instance("next"))
        insertPosition(start)

        consumer.start()
        awaitPosition(start + 2)

        assertThat(events.findAll().map { it.name }).containsExactly("instance-registration-error")
        assertThat(labels()).containsExactly("next")
        assertThat(endOffset(REGISTERED) - registeredBefore).isEqualTo(1)
    }

    private fun send(instance: InstanceObjectDto) = send(objectMapper.writeValueAsBytes(instance), headers())

    private fun send(
        value: ByteArray?,
        headers: InstanceFlowHeaders?,
    ) {
        val record = ProducerRecord<String, ByteArray>(RECEIVED, value)
        headers?.let { record.headers().add(headersMapper.toHeader(it)) }
        producer.send(record).get(20, TimeUnit.SECONDS)
    }

    private fun insertPosition(nextOffset: Long) {
        jdbc.update(
            "INSERT INTO instance_received_offset (topic_name, partition_id, next_offset) VALUES (?, 0, ?)",
            RECEIVED,
            nextOffset,
        )
    }

    private fun awaitPosition(nextOffset: Long) {
        await().atMost(Duration.ofSeconds(60)).until {
            val positions = jdbc.queryForList("SELECT next_offset FROM instance_received_offset", Long::class.java)
            positions == listOf(nextOffset)
        }
    }

    private fun endOffset(topic: String): Long {
        val partition = TopicPartition(topic, 0)
        if (topic !in admin.listTopics().names().get(20, TimeUnit.SECONDS)) return 0
        return admin
            .listOffsets(mapOf(partition to OffsetSpec.latest()))
            .partitionResult(partition)
            .get(20, TimeUnit.SECONDS)
            .offset()
    }

    private fun labels(): List<String> =
        jdbc.queryForList(
            "SELECT value FROM $INSTANCE_SCHEMA.instance_object_value_per_key WHERE key = 'label'",
            String::class.java,
        )

    private fun instance(label: String) = InstanceObjectDto(valuePerKey = mutableMapOf("label" to label))

    private fun headers(): InstanceFlowHeaders =
        InstanceFlowHeaders
            .builder()
            .sourceApplicationId(1L)
            .sourceApplicationIntegrationId("integration")
            .sourceApplicationInstanceId("source-instance")
            .correlationId(UUID.randomUUID())
            .build()

    @TestConfiguration(proxyBeanMethods = false)
    @ComponentScan(basePackages = ["no.novari.kafka", "no.novari.flyt.kafka"])
    class KafkaLibraries

    companion object {
        private const val INSTANCE_SCHEMA = "mapping_test_instance"
        private const val RECEIVED = "$ORG_ID.flyt.event.instance-received"
        private const val REGISTERED = "$ORG_ID.flyt.event.instance-registered"
        private const val GROUP = "consumer-test-instance-received"

        @JvmField @Container
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:17").withInitScript("db/instance-fixture/init.sql")

        @JvmField @Container
        val kafka: KafkaContainer =
            KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"))
                .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")

        @JvmStatic @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            postgres.start()
            kafka.start()
            // Created before the context starts, so the consumer never auto-creates it first.
            Admin.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers)).use {
                it.createTopics(listOf(NewTopic(RECEIVED, 1, 1))).all().get(20, TimeUnit.SECONDS)
            }
            registry.add("fint.database.url", postgres::getJdbcUrl)
            registry.add("fint.database.username", postgres::getUsername)
            registry.add("fint.database.password", postgres::getPassword)
            registry.add("spring.datasource.hikari.schema") { "consumer_test_history" }
            registry.add("novari.flyt.instance.database.schema") { INSTANCE_SCHEMA }
            registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers)
        }
    }
}

private const val ORG_ID = "consumer-test"
