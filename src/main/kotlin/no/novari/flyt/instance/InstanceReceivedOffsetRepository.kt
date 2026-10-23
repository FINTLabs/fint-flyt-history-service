package no.novari.flyt.instance

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
    @Transactional(propagation = Propagation.MANDATORY)
    fun lockNextOffset(messageId: InstanceReceivedMessageId): Long {
        val position =
            jdbcTemplate
                .query(
                    """
                    SELECT cluster_id, topic_id, next_offset
                    FROM instance_received_offset
                    WHERE topic_name = ? AND partition_id = ?
                    FOR UPDATE
                    """.trimIndent(),
                    { resultSet, _ ->
                        Position(
                            resultSet.getString("cluster_id"),
                            resultSet.getObject("topic_id", UUID::class.java),
                            resultSet.getLong("next_offset"),
                        )
                    },
                    messageId.topic,
                    messageId.partition,
                ).singleOrNull()
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

    private data class Position(
        val clusterId: String?,
        val topicId: UUID?,
        val nextOffset: Long,
    )
}
