package no.novari.flyt.instance.kafka

import no.novari.flyt.history.Application
import no.novari.flyt.history.ClockConfiguration
import no.novari.flyt.history.JpaAuditingTestConfig
import no.novari.flyt.instance.InstanceDeletionService
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
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
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
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
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
        "novari.kafka.topic.org-id=$DISPATCH_ORG",
        "novari.kafka.default-replicas=1",
        "fint.application-id=$DISPATCH_ORG",
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
    InstanceDeletionService::class,
    InstanceDeletedEventProducerService::class,
    InstanceDispatchedConsumerConfiguration::class,
    InstanceDispatchedConsumerIntegrationTest.KafkaLibraries::class,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext
class InstanceDispatchedConsumerIntegrationTest {
    @Autowired lateinit var instances: InstanceService

    @Autowired lateinit var consumer: ConcurrentMessageListenerContainer<String, InstanceObjectDto>

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var headersMapper: InstanceFlowHeadersMapper

    @AfterEach
    fun stopConsumer() {
        consumer.stop()
    }

    @Test
    fun `dispatch uses its own group deletes the graph and publishes one deletion event`() {
        consumer.stop()
        val saved =
            instances.save(
                InstanceObjectDto(
                    valuePerKey = mutableMapOf("root" to "value"),
                    objectCollectionPerKey =
                        mutableMapOf(
                            "children" to listOf(InstanceObjectDto(valuePerKey = mutableMapOf("child" to "value"))),
                        ),
                ),
            )
        val headers =
            InstanceFlowHeaders
                .builder()
                .sourceApplicationId(1L)
                .sourceApplicationIntegrationId("integration")
                .sourceApplicationInstanceId("source-instance")
                .instanceId(requireNotNull(saved.id))
                .integrationId(42L)
                .configurationId(43L)
                .archiveInstanceId("archive-instance")
                .fileIds(listOf(UUID.randomUUID()))
                .correlationId(UUID.randomUUID())
                .build()
        val source = TopicPartition(DISPATCHED, 0)
        val destination = TopicPartition(DELETED, 0)
        Admin.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers)).use { admin ->
            val offset =
                KafkaProducer<String, ByteArray>(
                    mapOf(
                        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java,
                    ),
                ).use { sender ->
                    val record = ProducerRecord<String, ByteArray>(DISPATCHED, null)
                    record.headers().add(headersMapper.toHeader(headers))
                    sender.send(record).get(20, TimeUnit.SECONDS).offset()
                }
            // Existing history has already consumed this record; deletion must use its own group.
            admin
                .alterConsumerGroupOffsets(DISPATCH_ORG, mapOf(source to OffsetAndMetadata(offset + 1)))
                .all()
                .get(20, TimeUnit.SECONDS)
            KafkaConsumer<ByteArray, ByteArray>(
                mapOf(
                    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java,
                    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                ),
            ).use { outgoing ->
                outgoing.assign(listOf(destination))
                outgoing.seekToBeginning(listOf(destination))
                consumer.start()
                val received = mutableListOf<ConsumerRecord<ByteArray, ByteArray>>()
                await().atMost(Duration.ofSeconds(30)).until {
                    received.addAll(outgoing.poll(Duration.ofMillis(250)))
                    received.isNotEmpty()
                }
                assertThat(received).hasSize(1)
                val event = received.single()
                assertThat(event.key()).isNull()
                assertThat(event.value()).isNull()
                assertThat(headersMapper.getInstanceFlowHeaders(event.headers())).isEqualTo(headers)
                for (table in listOf(
                    "instance_object",
                    "instance_object_collection",
                    "instance_object_value_per_key",
                )) {
                    assertThat(
                        jdbc.queryForObject("SELECT count(*) FROM mapping_test_instance.$table", Long::class.java)!!,
                    ).isZero()
                }
                await().atMost(Duration.ofSeconds(30)).until {
                    admin
                        .listConsumerGroupOffsets("$DISPATCH_ORG-instance-dispatched")
                        .partitionsToOffsetAndMetadata()
                        .get(20, TimeUnit.SECONDS)[source]
                        ?.offset() == offset + 1
                }
                assertThat(
                    admin
                        .listOffsets(mapOf(destination to OffsetSpec.latest()))
                        .partitionResult(destination)
                        .get(20, TimeUnit.SECONDS)
                        .offset(),
                ).isEqualTo(1)
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @ComponentScan(basePackages = ["no.novari.kafka", "no.novari.flyt.kafka"])
    class KafkaLibraries

    companion object {
        private const val DISPATCHED = "$DISPATCH_ORG.flyt.event.instance-dispatched"
        private const val DELETED = "$DISPATCH_ORG.flyt.event.instance-deleted"

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
            Admin.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers)).use {
                it.createTopics(listOf(NewTopic(DISPATCHED, 1, 1))).all().get(20, TimeUnit.SECONDS)
            }
            registry.add("fint.database.url", postgres::getJdbcUrl)
            registry.add("fint.database.username", postgres::getUsername)
            registry.add("fint.database.password", postgres::getPassword)
            registry.add("spring.datasource.hikari.schema") { "dispatch_test_history" }
            registry.add("novari.flyt.instance.database.schema") { "mapping_test_instance" }
            registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers)
        }
    }
}

private const val DISPATCH_ORG = "dispatched-test"
