package no.novari.flyt.instance.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import no.novari.flyt.history.Application
import no.novari.flyt.history.InstanceErrorEventService
import no.novari.flyt.history.InstanceInfoEventService
import no.novari.flyt.history.JpaAuditingTestConfig
import no.novari.flyt.history.mapping.InstanceFlowHeadersMappingService
import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.instance.InstanceReceivedOffsetRepository
import no.novari.flyt.instance.InstanceReceivedPositionService
import no.novari.flyt.instance.InstanceRegistrationHandler
import no.novari.flyt.instance.InstanceRegistrationService
import no.novari.flyt.instance.InstanceService
import no.novari.flyt.instance.config.InstanceDatabaseConfiguration
import no.novari.flyt.instance.config.InstanceJpaConfiguration
import no.novari.flyt.instance.model.InstanceMappingService
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.instance.outbox.InstanceRegisteredOutboxCodec
import no.novari.flyt.instance.outbox.InstanceRegisteredOutboxConfiguration
import no.novari.flyt.instance.outbox.InstanceRegisteredOutboxRepository
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeadersMapper
import no.novari.kafka.health.KafkaConsumersHealthIndicator
import no.novari.kafka.topic.name.TopicNameService
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.admin.RecordsToDelete
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.boot.actuate.health.Status
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.CannotGetJdbcConnectionException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.listener.MessageListenerContainer
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
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(
    showSql = false,
    properties = [
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.enable_lazy_load_no_trans=false",
        "spring.jpa.open-in-view=false",
        "novari.flyt.instance-functionality.enabled=true",
        "fint.application-id=received-consumer-test",
    ],
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = [Application::class])
@ImportAutoConfiguration(JacksonAutoConfiguration::class)
@Import(
    JpaAuditingTestConfig::class,
    InstanceDatabaseConfiguration::class,
    InstanceJpaConfiguration::class,
    InstanceRegistrationService::class,
    InstanceRegistrationHandler::class,
    InstanceReceivedOffsetRepository::class,
    InstanceReceivedPositionService::class,
    InstanceReceivedKafkaConfiguration::class,
    InstanceReceivedKafkaMetadataService::class,
    InstanceReceivedEventListener::class,
    InstanceFlowHeadersMapper::class,
    InstanceRegisteredOutboxConfiguration::class,
    InstanceRegisteredOutboxRepository::class,
    InstanceRegisteredOutboxCodec::class,
    InstanceErrorEventService::class,
    InstanceService::class,
    InstanceMappingService::class,
    InstanceInfoEventService::class,
    InstanceFlowHeadersMappingService::class,
    InstanceReceivedEventConsumerIntegrationTest.KafkaTestConfiguration::class,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InstanceReceivedEventConsumerIntegrationTest {
    @Autowired lateinit var objectMapper: ObjectMapper

    @Autowired lateinit var headersMapper: InstanceFlowHeadersMapper

    @Autowired lateinit var metadata: InstanceReceivedKafkaMetadataService

    @Autowired lateinit var listener: InstanceReceivedEventListener

    @Autowired lateinit var consumerConfig: ConsumerConfig

    @Autowired lateinit var names: TopicNameService

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var events: EventRepository

    @Autowired lateinit var context: ApplicationContext

    @MockitoSpyBean lateinit var handler: InstanceRegistrationHandler

    private val containers = mutableListOf<ConcurrentMessageListenerContainer<String, ByteArray>>()
    private lateinit var producer: KafkaProducer<String, ByteArray>
    private lateinit var admin: Admin
    private var startOffset = 0L

    @BeforeEach
    fun initializePositions() {
        admin = Admin.create(mapOf(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers))
        if (TOPIC !in admin.listTopics().names().get(20, TimeUnit.SECONDS)) {
            admin.createTopics(listOf(NewTopic(TOPIC, 2, 1))).all().get(20, TimeUnit.SECONDS)
        }
        val snapshot = metadata.read()
        startOffset = snapshot.partitions.getValue(0).endOffset
        snapshot.partitions.forEach { (partition, bounds) ->
            jdbc.update(
                "INSERT INTO instance_received_offset (topic_name, partition_id, next_offset, cluster_id, topic_id) VALUES (?, ?, ?, ?, ?)",
                TOPIC,
                partition,
                bounds.endOffset,
                snapshot.clusterId,
                snapshot.topicId,
            )
        }
        producer =
            KafkaProducer(
                mapOf(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java,
                ),
            )
    }

    @AfterEach
    fun cleanup() {
        containers.reversed().forEach { it.stop() }
        producer.close()
        admin.close()
        jdbc.execute("DELETE FROM instance_registered_outbox")
        events.deleteAll()
        jdbc.execute("DELETE FROM instance_received_offset")
        jdbc.execute("DELETE FROM mapping_test_instance.instance_object_value_per_key")
        jdbc.execute("UPDATE mapping_test_instance.instance_object SET instance_object_collection_id = NULL")
        jdbc.execute("UPDATE mapping_test_instance.instance_object_collection SET instance_object_id = NULL")
        jdbc.execute("DELETE FROM mapping_test_instance.instance_object_collection")
        jdbc.execute("DELETE FROM mapping_test_instance.instance_object")
    }

    @Test
    fun `database seek overrides committed Kafka offset and restart does not register old records again`() {
        send(instance("skipped"))
        send(instance("kept"))
        jdbc.update("UPDATE instance_received_offset SET next_offset = ? WHERE partition_id = 0", startOffset + 1)
        commitKafkaPosition(startOffset)

        val first = startConsumer()
        awaitOffset(startOffset + 2)
        assertThat(count("event")).isEqualTo(1)
        assertThat(count("instance_registered_outbox")).isEqualTo(1)
        assertThat(labels()).containsExactly("kept")

        first.stop()
        commitKafkaPosition(startOffset)
        startConsumer()
        send(instance("after restart"))
        awaitOffset(startOffset + 3)
        assertThat(count("event")).isEqualTo(2)
        assertThat(count("instance_registered_outbox")).isEqualTo(2)
        assertThat(labels()).containsExactlyInAnyOrder("kept", "after restart")
    }

    @Test
    fun `invalid graph is rejected once and the next broker record registers`() {
        send(InstanceObjectDto(valuePerKey = mutableMapOf("x".repeat(256) to "invalid")))
        send(instance("next"))
        startConsumer()

        awaitOffset(startOffset + 2)
        assertThat(count("event")).isEqualTo(2)
        assertThat(count("error")).isEqualTo(1)
        assertThat(count("instance_registered_outbox")).isEqualTo(1)
        assertThat(labels()).containsExactly("next")
        assertThat(
            events.findAll().map {
                it.name
            },
        ).containsExactlyInAnyOrder("instance-registration-error", "instance-registered")
    }

    @Test
    fun `transient infrastructure error retries the same record and then succeeds`() {
        doThrow(CannotGetJdbcConnectionException("secret-database-details"))
            .doCallRealMethod()
            .whenever(handler)
            .handle(any(), any(), any(), any())
        send(instance("retried"))
        startConsumer()

        awaitOffset(startOffset + 1)
        verify(handler, times(2)).handle(any(), any(), any(), any())
        assertThat(count("error")).isZero()
        assertThat(count("event")).isEqualTo(1)
        assertThat(count("instance_registered_outbox")).isEqualTo(1)
    }

    @Test
    fun `retry after database commit detects duplicate and restores expected progress`() {
        val firstAttempt = AtomicBoolean(true)
        doAnswer { invocation ->
            val result = invocation.callRealMethod()
            if (firstAttempt.getAndSet(false)) throw CannotGetJdbcConnectionException("simulated lost completion")
            result
        }.whenever(handler).handle(any(), any(), any(), any())
        send(instance("first"))
        send(instance("second"))
        startConsumer()

        awaitOffset(startOffset + 2)
        verify(handler, times(3)).handle(any(), any(), any(), any())
        assertThat(count("event")).isEqualTo(2)
        assertThat(count("error")).isZero()
        assertThat(count("instance_registered_outbox")).isEqualTo(2)
        assertThat(labels()).containsExactlyInAnyOrder("first", "second")
    }

    @Test
    @ExtendWith(OutputCaptureExtension::class)
    fun `exhausted retries stop before later records expose DOWN health and redact source content`(
        output: CapturedOutput,
    ) {
        doThrow(CannotGetJdbcConnectionException("secret-database-details"))
            .whenever(handler)
            .handle(any(), any(), any(), any())
        send(instance("secret-payload"))
        send(instance("must not run"))
        val container = startConsumer()
        val health = consumerHealth(container)

        await().atMost(30, TimeUnit.SECONDS).until { !container.isRunning }
        assertThat(health.health().status).isEqualTo(Status.DOWN)
        assertThat(nextOffset()).isEqualTo(startOffset)
        verify(handler, times(4)).handle(any(), any(), any(), any())
        assertThat(count("event")).isZero()
        assertThat(count("instance_registered_outbox")).isZero()
        assertThat(labels()).isEmpty()
        assertThat(
            output.all,
        ).doesNotContain("secret-payload", "secret-key", "secret-database-details", "private-task20-header")
    }

    @ParameterizedTest
    @ValueSource(strings = ["payload", "headers", "missing-headers", "header-length", "unknown"])
    fun `unresolved failures stop without skipping or creating business errors`(failureType: String) {
        when (failureType) {
            "payload" -> {
                sendRaw("secret-invalid-json".toByteArray())
            }

            "headers" -> {
                sendRaw(
                    objectMapper.writeValueAsBytes(instance("invalid headers")),
                    "secret-invalid-headers".toByteArray(),
                )
            }

            "missing-headers" -> {
                sendRaw(objectMapper.writeValueAsBytes(instance("missing headers")), null)
            }

            "header-length" -> {
                sendRaw(
                    objectMapper.writeValueAsBytes(instance("invalid length")),
                    headersMapper
                        .toHeader(
                            headers().toBuilder().sourceApplicationInstanceId("x".repeat(256)).build(),
                        ).value(),
                )
            }

            else -> {
                doThrow(
                    IllegalStateException("secret-unknown-details"),
                ).whenever(handler).handle(any(), any(), any(), any())
                send(instance("unknown failure"))
            }
        }
        send(instance("must not run"))
        val container = startConsumer()
        await().atMost(30, TimeUnit.SECONDS).until { !container.isRunning }

        assertThat(consumerHealth(container).health().status).isEqualTo(Status.DOWN)
        assertThat(nextOffset()).isEqualTo(startOffset)
        assertThat(count("event")).isZero()
        assertThat(count("instance_registered_outbox")).isZero()
        assertThat(labels()).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["missing", "unbound", "generation", "bounds"])
    fun `invalid initial database position stops assignment without fallback`(invalidState: String) {
        send(instance("must not run"))
        when (invalidState) {
            "missing" -> {
                jdbc.update("DELETE FROM instance_received_offset WHERE partition_id = 0")
            }

            "unbound" -> {
                jdbc.update(
                    "UPDATE instance_received_offset SET cluster_id = NULL, topic_id = NULL WHERE partition_id = 0",
                )
            }

            "generation" -> {
                jdbc.update(
                    "UPDATE instance_received_offset SET topic_id = ? WHERE partition_id = 0",
                    UUID.randomUUID(),
                )
            }

            else -> {
                jdbc.update(
                    "UPDATE instance_received_offset SET next_offset = ? WHERE partition_id = 0",
                    startOffset + 100,
                )
            }
        }
        val before = jdbc.queryForList("SELECT * FROM instance_received_offset ORDER BY partition_id")
        val container = startConsumer()
        await().atMost(30, TimeUnit.SECONDS).until { !container.isRunning }

        verify(handler, times(0)).handle(any(), any(), any(), any())
        assertThat(consumerHealth(container).health().status).isEqualTo(Status.DOWN)
        assertThat(jdbc.queryForList("SELECT * FROM instance_received_offset ORDER BY partition_id")).isEqualTo(before)
        assertThat(count("event")).isZero()
    }

    @Test
    fun `broker retention expiring a database position stops without offset reset`() {
        send(instance("expired"))
        send(instance("retained"))
        admin
            .deleteRecords(mapOf(TopicPartition(TOPIC, 0) to RecordsToDelete.beforeOffset(startOffset + 1)))
            .all()
            .get(20, TimeUnit.SECONDS)
        val container = startConsumer()
        await().atMost(30, TimeUnit.SECONDS).until { !container.isRunning }

        assertThat(nextOffset()).isEqualTo(startOffset)
        assertThat(count("event")).isZero()
        assertThat(consumerHealth(container).health().status).isEqualTo(Status.DOWN)
    }

    @Test
    fun `rebalance revalidates assignments while partitions keep sequential database progress`() {
        val secondStart = nextOffset(1)
        val first = startConsumer()
        await().atMost(30, TimeUnit.SECONDS).until { first.assignedPartitions.size == 2 }
        send(instance("partition zero"))
        send(instance("partition one"), 1)
        awaitOffset(startOffset + 1)
        await().atMost(30, TimeUnit.SECONDS).until { nextOffset(1) == secondStart + 1 }

        val second = startConsumer()
        await().atMost(30, TimeUnit.SECONDS).until {
            first.assignedPartitions.size == 1 && second.assignedPartitions.size == 1
        }
        send(instance("zero after rebalance"))
        send(instance("one after rebalance"), 1)
        awaitOffset(startOffset + 2)
        await().atMost(30, TimeUnit.SECONDS).until { nextOffset(1) == secondStart + 2 }
        assertThat(count("event")).isEqualTo(4)
        assertThat(count("instance_registered_outbox")).isEqualTo(4)
        assertThat(count("error")).isZero()
    }

    private fun startConsumer(): ConcurrentMessageListenerContainer<String, ByteArray> {
        val config = InstanceReceivedEventConsumerConfiguration()
        val container =
            config.instanceReceivedEventConsumer(
                config.instanceReceivedConsumerFactory(consumerConfig),
                names,
                listener,
                InstanceReceivedKafkaProperties(retryAttempts = 3, retryInterval = Duration.ofMillis(20)),
            )
        container.setApplicationEventPublisher(context)
        containers += container
        container.start()
        return container
    }

    private fun consumerHealth(container: MessageListenerContainer): KafkaConsumersHealthIndicator {
        val beans = StaticListableBeanFactory(mapOf("instanceReceivedEventConsumer" to container))
        return KafkaConsumersHealthIndicator(beans.getBeanProvider(MessageListenerContainer::class.java)).apply {
            onApplicationReady(mock<ApplicationReadyEvent>())
        }
    }

    private fun send(
        instance: InstanceObjectDto,
        partition: Int = 0,
    ) {
        sendRaw(objectMapper.writeValueAsBytes(instance), headersMapper.toHeader(headers()).value(), partition)
    }

    private fun sendRaw(
        payload: ByteArray,
        headerBytes: ByteArray? = headersMapper.toHeader(headers()).value(),
        partition: Int = 0,
    ) {
        val record = ProducerRecord(TOPIC, partition, "secret-key", payload)
        if (headerBytes != null) record.headers().add("flyt.instance-flow-headers", headerBytes)
        record.headers().add("private-task20-header", "secret-header-value".toByteArray())
        producer.send(record).get(20, TimeUnit.SECONDS)
    }

    private fun commitKafkaPosition(offset: Long) {
        admin
            .alterConsumerGroupOffsets(GROUP, mapOf(TopicPartition(TOPIC, 0) to OffsetAndMetadata(offset)))
            .all()
            .get(20, TimeUnit.SECONDS)
    }

    private fun awaitOffset(offset: Long) {
        await().atMost(30, TimeUnit.SECONDS).until { nextOffset() == offset }
    }

    private fun nextOffset(partition: Int = 0): Long =
        jdbc.queryForObject(
            "SELECT next_offset FROM instance_received_offset WHERE partition_id = ?",
            Long::class.java,
            partition,
        )!!

    private fun count(table: String): Long = jdbc.queryForObject("SELECT count(*) FROM $table", Long::class.java)!!

    private fun labels(): List<String> =
        jdbc.queryForList(
            "SELECT value FROM mapping_test_instance.instance_object_value_per_key WHERE key = 'label'",
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
    class KafkaTestConfiguration {
        @Bean fun clock(): Clock = Clock.systemUTC()

        @Bean fun topicNameService(): TopicNameService =
            mock<TopicNameService>().also {
                whenever(it.validateAndMapToTopicName(any())).thenReturn(TOPIC)
            }

        @Bean fun consumerConfig() =
            ConsumerConfig(
                mapOf(
                    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                    ConsumerConfig.GROUP_ID_CONFIG to GROUP,
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                    ConsumerConfig.ISOLATION_LEVEL_CONFIG to "read_committed",
                    ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 10,
                    ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG to 6000,
                    ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG to 1000,
                ),
            )

        @Bean fun producerConfig() =
            ProducerConfig(
                mapOf(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java,
                ),
            )
    }

    companion object {
        private const val TOPIC = "task20-instance-received"
        private const val GROUP = "task20-history-service"

        @JvmField @Container
        val postgres = PostgreSQLContainer("postgres:17").withInitScript("db/instance-fixture/init.sql")

        @JvmField @Container
        val kafka =
            KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"))
                .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")

        @JvmStatic @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            postgres.start()
            kafka.start()
            registry.add("fint.database.url", postgres::getJdbcUrl)
            registry.add("fint.database.username", postgres::getUsername)
            registry.add("fint.database.password", postgres::getPassword)
            registry.add("spring.datasource.hikari.schema") { "received_consumer_history" }
            registry.add("novari.flyt.instance.database.schema") { "mapping_test_instance" }
        }
    }
}
