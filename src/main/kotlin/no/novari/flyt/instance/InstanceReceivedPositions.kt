package no.novari.flyt.instance

import io.github.oshai.kotlinlogging.KotlinLogging
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.springframework.kafka.listener.ConsumerSeekAware.ConsumerSeekCallback
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps each assigned partition aligned with `next_offset` in the database. Once the consumer reads from the database
 * position, the next record it gets is the next one to process, even when Kafka offsets have gaps. A consumer that
 * starts anywhere else, for example at a committed Kafka offset ahead of the position, is moved back first.
 */
@Component
@ConditionalOnInstanceFunctionality
class InstanceReceivedPositions(
    private val offsetRepository: InstanceReceivedOffsetRepository,
) {
    private val logger = KotlinLogging.logger {}
    private val callbacks = ConcurrentHashMap<TopicPartition, ConsumerSeekCallback>()
    private val nextOffsets = ConcurrentHashMap<TopicPartition, Long>()

    // Records polled before a seek requested by the listener are still delivered; those above this offset are ignored.
    private val seekPending = ConcurrentHashMap<TopicPartition, Long>()

    fun assigned(
        partitions: Collection<TopicPartition>,
        callback: ConsumerSeekCallback,
    ) {
        partitions.forEach { partition ->
            callbacks[partition] = callback
            nextOffsets.remove(partition)
            seekPending.remove(partition)
            try {
                val nextOffset = storedPosition(partition)
                callback.seek(partition.topic(), partition.partition(), nextOffset)
                nextOffsets[partition] = nextOffset
            } catch (failure: Exception) {
                logger.error(failure) { "Could not align $partition with its database position; retrying per record" }
            }
        }
    }

    fun revoked(partitions: Collection<TopicPartition>) {
        partitions.forEach {
            callbacks.remove(it)
            nextOffsets.remove(it)
            seekPending.remove(it)
        }
    }

    /** Throws while the position row is missing or unreadable, so the error handler retries without effects. */
    fun isNext(record: ConsumerRecord<*, *>): Boolean {
        val partition = TopicPartition(record.topic(), record.partition())
        val offset = record.offset()
        seekPending[partition]?.let { seekFrom ->
            if (offset > seekFrom) return false
            seekPending.remove(partition)
        }
        val nextOffset = nextOffsets[partition]
        if (nextOffset == null) {
            // Not aligned at assignment, so the consumer started at its committed Kafka offset.
            val stored = storedPosition(partition)
            nextOffsets[partition] = stored
            if (offset > stored) {
                logger.warn { "Record $partition@$offset is ahead of next_offset=$stored; seeking back" }
                checkNotNull(callbacks[partition]) { "No seek callback for $partition" }
                    .seek(partition.topic(), partition.partition(), stored)
                seekPending[partition] = offset
                return false
            }
            return offset == stored
        }
        if (offset < nextOffset) return false
        if (offset > nextOffset) {
            // Kafka offsets are not contiguous, for example after transaction markers or retention.
            logger.warn { "Record $partition@$offset follows a gap after next_offset=$nextOffset" }
        }
        return true
    }

    /** Called only after the database position is committed. */
    fun advanced(record: ConsumerRecord<*, *>) {
        nextOffsets[TopicPartition(record.topic(), record.partition())] = record.offset() + 1
    }

    private fun storedPosition(partition: TopicPartition): Long =
        checkNotNull(offsetRepository.findNextOffset(partition.topic(), partition.partition())) {
            "No instance_received_offset row for $partition; processing waits for it"
        }
}
