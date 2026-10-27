package no.novari.flyt.instance.kafka

import no.novari.flyt.instance.receivedConsumerConfig
import no.novari.kafka.topic.name.TopicNameService
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.admin.RecordsToDelete
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID
import java.util.concurrent.TimeUnit

@Testcontainers(disabledWithoutDocker = true)
class InstanceReceivedKafkaMetadataIntegrationTest {
    @Test
    fun `metadata returns the real identity retained offset range and empty partition`() {
        Admin.create(mapOf(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers)).use { admin ->
            val topic = "received-metadata-${UUID.randomUUID()}"
            admin.createTopics(listOf(NewTopic(topic, 2, 1))).all().get(20, TimeUnit.SECONDS)
            KafkaProducer<String, String>(producerConfiguration()).use { producer ->
                repeat(2) { producer.send(ProducerRecord(topic, 0, null, "instance-$it")).get(20, TimeUnit.SECONDS) }
            }
            val partition = TopicPartition(topic, 0)
            admin.deleteRecords(mapOf(partition to RecordsToDelete.beforeOffset(1))).all().get(20, TimeUnit.SECONDS)

            val metadata = service(topic).read()
            val id =
                admin
                    .describeTopics(listOf(topic))
                    .allTopicNames()
                    .get()
                    .getValue(topic)
                    .topicId()

            assertThat(metadata.clusterId).isEqualTo(admin.describeCluster().clusterId().get())
            assertThat(metadata.topicId).isEqualTo(UUID(id.mostSignificantBits, id.leastSignificantBits))
            assertThat(metadata.partitions).containsExactlyInAnyOrderEntriesOf(
                mapOf(0 to InstanceReceivedPartitionOffsets(1, 2), 1 to InstanceReceivedPartitionOffsets(0, 0)),
            )
        }
    }

    @Test
    fun `read committed end position excludes an open producer transaction`() {
        Admin.create(mapOf(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers)).use { admin ->
            val topic = "received-transaction-${UUID.randomUUID()}"
            admin.createTopics(listOf(NewTopic(topic, 1, 1))).all().get(20, TimeUnit.SECONDS)
            KafkaProducer<String, String>(
                producerConfiguration() + (ProducerConfig.TRANSACTIONAL_ID_CONFIG to UUID.randomUUID().toString()),
            ).use { producer ->
                producer.initTransactions()
                producer.beginTransaction()
                try {
                    producer.send(ProducerRecord(topic, 0, null, "uncommitted-instance")).get(20, TimeUnit.SECONDS)

                    assertThat(
                        service(topic, "read_uncommitted")
                            .read()
                            .partitions
                            .getValue(0)
                            .endOffset,
                    ).isEqualTo(1)
                    assertThat(
                        service(topic, "read_committed")
                            .read()
                            .partitions
                            .getValue(0)
                            .endOffset,
                    ).isZero()
                } finally {
                    producer.abortTransaction()
                }
            }
        }
    }

    private fun service(
        topic: String,
        isolation: String = "read_uncommitted",
    ): InstanceReceivedKafkaMetadataService {
        val names: TopicNameService = mock()
        whenever(names.validateAndMapToTopicName(any())).thenReturn(topic)
        val properties = InstanceReceivedKafkaProperties()
        val supplier =
            InstanceReceivedKafkaConfiguration().instanceReceivedMetadataAdminSupplier(
                ProducerConfig(producerConfiguration()),
                properties,
            )
        return InstanceReceivedKafkaMetadataService(supplier, names, properties, receivedConsumerConfig(isolation))
    }

    private fun producerConfiguration(): Map<String, Any> =
        mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        )

    companion object {
        @JvmField
        @Container
        val kafka =
            KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"))
                .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
                .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
    }
}
