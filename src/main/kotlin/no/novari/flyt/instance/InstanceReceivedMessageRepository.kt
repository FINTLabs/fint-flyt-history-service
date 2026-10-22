package no.novari.flyt.instance

import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
@ConditionalOnInstanceFunctionality
class InstanceReceivedMessageRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    @Transactional(propagation = Propagation.MANDATORY)
    fun tryInsert(messageId: InstanceReceivedMessageId): Boolean =
        jdbcTemplate.update(
            """
            INSERT INTO processed_instance_received (topic_name, partition_id, message_offset)
            VALUES (?, ?, ?)
            ON CONFLICT (topic_name, partition_id, message_offset) DO NOTHING
            """.trimIndent(),
            messageId.topic,
            messageId.partition,
            messageId.offset,
        ) == 1
}
