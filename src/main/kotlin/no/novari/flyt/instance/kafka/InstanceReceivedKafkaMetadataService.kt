package no.novari.flyt.instance.kafka

import no.novari.flyt.instance.InstanceReceivedOffsetException
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.kafka.topic.name.EventTopicNameParameters
import no.novari.kafka.topic.name.TopicNamePrefixParameters
import no.novari.kafka.topic.name.TopicNameService
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.ListOffsetsOptions
import org.apache.kafka.clients.admin.OffsetSpec
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.IsolationLevel
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.Uuid
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

@Service
@ConditionalOnInstanceFunctionality
@Transactional(propagation = Propagation.NEVER)
class InstanceReceivedKafkaMetadataService(
    @param:Qualifier("instanceReceivedMetadataAdminSupplier")
    private val adminSupplier: Supplier<Admin>,
    topicNameService: TopicNameService,
    private val properties: InstanceReceivedKafkaProperties,
    consumerConfig: ConsumerConfig,
) {
    private val isolationLevel =
        IsolationLevel.valueOf(consumerConfig.getString(ConsumerConfig.ISOLATION_LEVEL_CONFIG).uppercase(Locale.ROOT))
    private val topic =
        topicNameService.validateAndMapToTopicName(
            EventTopicNameParameters
                .builder()
                .topicNamePrefixParameters(
                    TopicNamePrefixParameters
                        .stepBuilder()
                        .orgIdApplicationDefault()
                        .domainContextApplicationDefault()
                        .build(),
                ).eventName("instance-received")
                .build(),
        )

    fun read(): InstanceReceivedTopicMetadata {
        try {
            return adminSupplier.get().use { admin ->
                val cluster =
                    admin.describeCluster().clusterId().get(
                        properties.metadataTimeout.toMillis(),
                        TimeUnit.MILLISECONDS,
                    )
                val description =
                    admin
                        .describeTopics(listOf(topic))
                        .allTopicNames()
                        .get(properties.metadataTimeout.toMillis(), TimeUnit.MILLISECONDS)
                        .getValue(topic)
                val topicId = description.topicId()
                if (cluster.isNullOrBlank() || topicId == null || topicId == Uuid.ZERO_UUID) {
                    throw InstanceReceivedOffsetException("Missing cluster or topic identity for topic=$topic")
                }
                val partitions = description.partitions().map { TopicPartition(topic, it.partition()) }
                if (partitions.isEmpty()) {
                    throw InstanceReceivedOffsetException("No partitions found for topic=$topic")
                }
                val earliestOffsets = partitions.associateWith { OffsetSpec.earliest() }
                val latestOffsets = partitions.associateWith { OffsetSpec.latest() }
                val earliest = admin.listOffsets(earliestOffsets, ListOffsetsOptions(isolationLevel)).all()
                val latest = admin.listOffsets(latestOffsets, ListOffsetsOptions(isolationLevel)).all()
                val starts = earliest.get(properties.metadataTimeout.toMillis(), TimeUnit.MILLISECONDS)
                val ends = latest.get(properties.metadataTimeout.toMillis(), TimeUnit.MILLISECONDS)
                val bounds =
                    partitions.associate { partition ->
                        val start = starts[partition]?.offset()
                        val end = ends[partition]?.offset()
                        if (start == null || end == null || start < 0 || end < start) {
                            throw InstanceReceivedOffsetException(
                                "Invalid offset bounds for topic=$topic, partition=${partition.partition()}",
                            )
                        }
                        partition.partition() to InstanceReceivedPartitionOffsets(start, end)
                    }
                InstanceReceivedTopicMetadata(
                    topic,
                    cluster,
                    UUID(topicId.mostSignificantBits, topicId.leastSignificantBits),
                    bounds,
                )
            }
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InstanceReceivedOffsetException("Broker metadata lookup interrupted for topic=$topic", failure)
        } catch (failure: InstanceReceivedOffsetException) {
            throw failure
        } catch (failure: Exception) {
            throw InstanceReceivedOffsetException("Unable to read broker metadata for topic=$topic", failure)
        }
    }
}
