package no.novari.flyt.instance

import io.github.oshai.kotlinlogging.KotlinLogging
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.springframework.kafka.listener.ConsumerSeekAware.ConsumerSeekCallback
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps each assigned partition aligned with `next_offset` in the database. A record is only processed when it is
 * exactly the next one; a consumer that starts anywhere else, for example at a committed Kafka offset, is moved back
 * to the database position first.
 */
@Component
@ConditionalOnInstanceFunctionality
class InstanceReceivedPositions(
    private val offsetRepository: InstanceReceivedOffsetRepository,
) {
    private val logger = KotlinLogging.logger {}
    private val callbacks = ConcurrentHashMap<TopicPartition, ConsumerSeekCallback>()
    private val nextOffsets = ConcurrentHashMap<TopicPartition, Long>()

    fun assigned(
        partitions: Collection<TopicPartition>,
        callback: ConsumerSeekCallback,
    ) {
        partitions.forEach { partition ->
            callbacks[partition] = callback
            nextOffsets.remove(partition)
            try {
                seekToStoredPosition(partition)
            } catch (failure: Exception) {
                logger.error(failure) { "Could not align $partition with its database position; retrying per record" }
            }
        }
    }

    fun revoked(partitions: Collection<TopicPartition>) {
        partitions.forEach {
            callbacks.remove(it)
            nextOffsets.remove(it)
        }
    }

    /** Throws while the position row is missing or unreadable, so the error handler retries without effects. */
    fun isNext(record: ConsumerRecord<*, *>): Boolean {
        val partition = TopicPartition(record.topic(), record.partition())
        val nextOffset = nextOffsets[partition] ?: seekToStoredPosition(partition)
        if (record.offset() > nextOffset) {
            // Earlier records may be missing: start over from the database position instead of skipping them.
            logger.warn { "Record $partition@${record.offset()} is ahead of next_offset=$nextOffset; seeking back" }
            checkNotNull(callbacks[partition]) { "No seek callback for $partition" }
                .seek(partition.topic(), partition.partition(), nextOffset)
        }
        return record.offset() == nextOffset
    }

    /** Called only after the database position is committed. */
    fun advanced(record: ConsumerRecord<*, *>) {
        nextOffsets[TopicPartition(record.topic(), record.partition())] = record.offset() + 1
    }

    private fun seekToStoredPosition(partition: TopicPartition): Long {
        val nextOffset =
            checkNotNull(offsetRepository.findNextOffset(partition.topic(), partition.partition())) {
                "No instance_received_offset row for $partition; processing waits for it"
            }
        checkNotNull(callbacks[partition]) { "No seek callback for $partition" }
            .seek(partition.topic(), partition.partition(), nextOffset)
        nextOffsets[partition] = nextOffset
        return nextOffset
    }
}
