package no.novari.flyt.instance

import io.github.oshai.kotlinlogging.KotlinLogging
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
@ConditionalOnInstanceFunctionality
class InstanceReceivedOffsetRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val logger = KotlinLogging.logger {}

    @Transactional(readOnly = true)
    fun find(
        topic: String,
        partition: Int,
    ): InstanceReceivedPosition? = readPosition(topic, partition, false)

    @Transactional(propagation = Propagation.MANDATORY)
    fun lockForProcessing(
        messageId: InstanceReceivedMessageId,
        expectedNextOffset: Long,
    ): Long? {
        if (expectedNextOffset < 0) {
            throw InstanceReceivedOffsetException("Expected next offset must not be negative")
        }
        val nextOffset = lockNextOffset(messageId)
        if (messageId.offset < nextOffset) {
            logger.info { "Skipping already processed instance-received record: $messageId, nextOffset=$nextOffset" }
            return null
        }
        if (nextOffset != expectedNextOffset) {
            throw InstanceReceivedOffsetException(
                "Unexpected instance-received position for topic=${messageId.topic}, " +
                    "partition=${messageId.partition}: " +
                    "storedNextOffset=$nextOffset, expectedNextOffset=$expectedNextOffset, recordOffset=${messageId.offset}",
            )
        }
        return nextOffset
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun lockNextOffset(messageId: InstanceReceivedMessageId): Long {
        val position =
            readPosition(messageId.topic, messageId.partition, true)
                ?: throw InstanceReceivedOffsetException(
                    "No instance-received offset initialized for topic=${messageId.topic}, partition=${messageId.partition}",
                )
        if (position.clusterId == null || position.topicId == null) {
            throw InstanceReceivedOffsetException(
                "Topic generation is not initialized for topic=${messageId.topic}, partition=${messageId.partition}",
            )
        }
        if (position.clusterId != messageId.clusterId || position.topicId != messageId.topicId) {
            throw InstanceReceivedOffsetException(
                "Topic generation mismatch for topic=${messageId.topic}, partition=${messageId.partition}: " +
                    "storedClusterId=${position.clusterId}, storedTopicId=${position.topicId}, " +
                    "currentClusterId=${messageId.clusterId}, currentTopicId=${messageId.topicId}",
            )
        }
        return position.nextOffset
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun initialize(request: InstanceReceivedPositionInitialization): InstanceReceivedPosition {
        val desired =
            InstanceReceivedPosition(
                request.topic,
                request.partition,
                request.nextOffset,
                request.clusterId,
                request.topicId,
            )
        if (request.expectedPosition == ExpectedInstanceReceivedPosition.Missing) {
            val inserted =
                jdbcTemplate.update(
                    """
                    INSERT INTO instance_received_offset (topic_name, partition_id, next_offset, cluster_id, topic_id)
                    VALUES (?, ?, ?, ?, ?) ON CONFLICT (topic_name, partition_id) DO NOTHING
                    """.trimIndent(),
                    request.topic,
                    request.partition,
                    request.nextOffset,
                    request.clusterId,
                    request.topicId,
                )
            if (inserted == 1) return desired
        }
        val actual = readPosition(request.topic, request.partition, true)
        if (actual == desired) return actual
        if (actual != null && request.expectedPosition == ExpectedInstanceReceivedPosition.Legacy &&
            actual.clusterId == null && actual.topicId == null && actual.nextOffset == request.nextOffset
        ) {
            val updated =
                jdbcTemplate.update(
                    """
                    UPDATE instance_received_offset SET cluster_id = ?, topic_id = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE topic_name = ? AND partition_id = ? AND next_offset = ?
                        AND cluster_id IS NULL AND topic_id IS NULL
                    """.trimIndent(),
                    request.clusterId,
                    request.topicId,
                    request.topic,
                    request.partition,
                    request.nextOffset,
                )
            if (updated == 1) return desired
        }
        throw InstanceReceivedOffsetException(
            "Instance-received position does not match the initialization basis for topic=${request.topic}, partition=${request.partition}",
        )
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun advance(
        messageId: InstanceReceivedMessageId,
        expectedNextOffset: Long,
    ) {
        val updated =
            jdbcTemplate.update(
                """
                UPDATE instance_received_offset
                SET next_offset = ?, updated_at = CURRENT_TIMESTAMP
                WHERE topic_name = ? AND partition_id = ? AND cluster_id = ? AND topic_id = ?
                  AND next_offset = ? AND next_offset <= ?
                """.trimIndent(),
                Math.incrementExact(messageId.offset),
                messageId.topic,
                messageId.partition,
                messageId.clusterId,
                messageId.topicId,
                expectedNextOffset,
                messageId.offset,
            )
        if (updated != 1) {
            throw InstanceReceivedOffsetException(
                "Instance-received position changed for topic=${messageId.topic}, partition=${messageId.partition}",
            )
        }
    }

    private fun readPosition(
        topic: String,
        partition: Int,
        lock: Boolean,
    ): InstanceReceivedPosition? =
        jdbcTemplate
            .query(
                """
                SELECT cluster_id, topic_id, next_offset
                FROM instance_received_offset WHERE topic_name = ? AND partition_id = ?
                """.trimIndent() + if (lock) " FOR UPDATE" else "",
                { row, _ ->
                    InstanceReceivedPosition(
                        topic,
                        partition,
                        row.getLong("next_offset"),
                        row.getString("cluster_id"),
                        row.getObject("topic_id", UUID::class.java),
                    )
                },
                topic,
                partition,
            ).singleOrNull()
}
