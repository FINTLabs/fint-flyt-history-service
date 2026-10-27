package no.novari.flyt.instance.kafka

import no.novari.flyt.history.kafka.eventTopicNameParameters
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.kafka.topic.name.TopicNameService
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.ListOffsetsOptions
import org.apache.kafka.clients.admin.OffsetSpec
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.IsolationLevel
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.Uuid
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.RetriableException
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import java.util.function.Supplier
import org.apache.kafka.common.errors.TimeoutException as KafkaTimeoutException

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
        topicNameService.validateAndMapToTopicName(eventTopicNameParameters(EventCategory.INSTANCE_RECEIVED))

    fun read(): InstanceReceivedTopicMetadata {
        val deadline = MetadataLookupDeadline(properties.metadataTimeout)
        try {
            val admin = adminSupplier.get()
            return AutoCloseable { admin.close(deadline.remaining()) }.use {
                val clusterFuture = admin.describeCluster().clusterId()
                val descriptionFuture = admin.describeTopics(listOf(topic)).allTopicNames()
                val cluster = deadline.await(clusterFuture)
                val description = deadline.await(descriptionFuture).getValue(topic)
                val topicId = description.topicId()
                if (cluster.isNullOrBlank() || topicId == null || topicId == Uuid.ZERO_UUID) {
                    throw InstanceReceivedMetadataException("Missing cluster or topic identity for topic=$topic")
                }
                val partitions = description.partitions().map { TopicPartition(topic, it.partition()) }
                if (partitions.isEmpty()) {
                    throw InstanceReceivedMetadataException("No partitions found for topic=$topic")
                }
                val earliestOffsets = partitions.associateWith { OffsetSpec.earliest() }
                val latestOffsets = partitions.associateWith { OffsetSpec.latest() }
                val earliest = admin.listOffsets(earliestOffsets, ListOffsetsOptions(isolationLevel)).all()
                val latest = admin.listOffsets(latestOffsets, ListOffsetsOptions(isolationLevel)).all()
                val starts = deadline.await(earliest)
                val ends = deadline.await(latest)
                val bounds =
                    partitions.associate { partition ->
                        val start = starts[partition]?.offset()
                        val end = ends[partition]?.offset()
                        if (start == null || end == null || start < 0 || end < start) {
                            throw InstanceReceivedMetadataException(
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
        } catch (failure: InstanceReceivedMetadataException) {
            throw failure
        } catch (failure: Exception) {
            throw metadataFailure(failure)
        }
    }

    private fun metadataFailure(failure: Exception): InstanceReceivedMetadataException {
        var cause: Throwable = failure
        while ((cause is ExecutionException || cause is CompletionException) && cause.cause != null) {
            cause = cause.cause!!
        }
        val message =
            when (cause) {
                is InterruptedException -> {
                    Thread.currentThread().interrupt()
                    "Broker metadata lookup interrupted"
                }

                is TimeoutException, is KafkaTimeoutException -> {
                    "Broker metadata lookup timed out"
                }

                is AuthorizationException -> {
                    "Not authorized to read broker metadata"
                }

                is UnknownTopicOrPartitionException -> {
                    "Topic is missing or unavailable"
                }

                is RetriableException -> {
                    "Broker metadata is temporarily unavailable"
                }

                else -> {
                    "Unable to read broker metadata"
                }
            }
        return InstanceReceivedMetadataException("$message for topic=$topic (${cause.javaClass.simpleName})", cause)
    }
}
