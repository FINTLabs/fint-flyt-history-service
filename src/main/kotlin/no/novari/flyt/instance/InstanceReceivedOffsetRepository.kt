package no.novari.flyt.instance

import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
@ConditionalOnInstanceFunctionality
class InstanceReceivedOffsetRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun findNextOffset(
        topic: String,
        partition: Int,
    ): Long? =
        jdbcTemplate
            .queryForList(
                "SELECT next_offset FROM instance_received_offset WHERE topic_name = ? AND partition_id = ?",
                Long::class.java,
                topic,
                partition,
            ).singleOrNull()

    // Not GREATEST: a recreated topic restarts at 0, and the position must follow it instead of staying ahead.
    fun markProcessed(
        topic: String,
        partition: Int,
        offset: Long,
    ) {
        val updated =
            jdbcTemplate.update(
                """
                UPDATE instance_received_offset SET next_offset = ?, updated_at = CURRENT_TIMESTAMP
                WHERE topic_name = ? AND partition_id = ?
                """.trimIndent(),
                offset + 1,
                topic,
                partition,
            )
        check(updated == 1) { "No instance_received_offset row for topic=$topic, partition=$partition" }
    }
}
