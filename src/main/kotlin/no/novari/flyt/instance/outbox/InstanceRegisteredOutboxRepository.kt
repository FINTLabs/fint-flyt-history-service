package no.novari.flyt.instance.outbox

import no.novari.flyt.instance.InstanceReceivedMessageId
import no.novari.flyt.instance.config.ConditionalOnInstanceFunctionality
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
@ConditionalOnInstanceFunctionality
@Transactional(propagation = Propagation.MANDATORY)
class InstanceRegisteredOutboxRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val codec: InstanceRegisteredOutboxCodec,
    private val properties: InstanceRegisteredOutboxProperties,
) {
    fun enqueue(
        messageId: InstanceReceivedMessageId,
        headers: InstanceFlowHeaders,
        instance: InstanceObjectDto,
        timestamp: Instant,
        applicationId: String,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO instance_registered_outbox
                (id, origin_cluster_id, origin_topic_id, origin_topic_name, origin_partition_id, origin_offset,
                 instance_flow_headers, instance_payload, event_timestamp, application_id)
            VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            messageId.clusterId,
            messageId.topicId,
            messageId.topic,
            messageId.partition,
            messageId.offset,
            codec.encodeHeaders(headers),
            codec.encodeInstance(instance),
            timestamp.atOffset(ZoneOffset.UTC),
            applicationId,
        )
    }

    fun claimNext(): InstanceRegisteredOutboxClaim? =
        jdbcTemplate
            .query(
                """
                WITH candidate AS (
                    SELECT pending.id
                    FROM instance_registered_outbox pending
                    WHERE pending.next_attempt_at <= clock_timestamp()
                      AND (pending.claimed_until IS NULL OR pending.claimed_until <= clock_timestamp())
                    ORDER BY pending.created_at, pending.id
                    LIMIT 1 FOR UPDATE OF pending SKIP LOCKED
                )
                UPDATE instance_registered_outbox outbox
                SET claim_token = ?, claimed_until = clock_timestamp() + ? * INTERVAL '1 millisecond',
                    attempts = outbox.attempts + 1
                FROM candidate WHERE outbox.id = candidate.id
                RETURNING outbox.*
                """.trimIndent(),
                { row, _ ->
                    InstanceRegisteredOutboxClaim(
                        row.getObject("id", UUID::class.java),
                        InstanceReceivedMessageId(
                            row.getString("origin_topic_name"),
                            row.getInt("origin_partition_id"),
                            row.getLong("origin_offset"),
                            row.getString("origin_cluster_id"),
                            row.getObject("origin_topic_id", UUID::class.java),
                        ),
                        row.getString("instance_flow_headers"),
                        row.getString("instance_payload"),
                        row.getObject("event_timestamp", OffsetDateTime::class.java).toInstant(),
                        row.getString("application_id"),
                        row.getObject("claim_token", UUID::class.java),
                        row.getInt("attempts"),
                    )
                },
                UUID.randomUUID(),
                properties.claimDuration.toMillis(),
            ).singleOrNull()

    fun complete(claim: InstanceRegisteredOutboxClaim): Boolean =
        jdbcTemplate.update(
            "DELETE FROM instance_registered_outbox WHERE id = ? AND claim_token = ?",
            claim.id,
            claim.token,
        ) == 1

    fun fail(
        claim: InstanceRegisteredOutboxClaim,
        errorType: String,
    ): Boolean =
        jdbcTemplate.update(
            """
            UPDATE instance_registered_outbox
            SET claim_token = NULL, claimed_until = NULL, last_error_type = ?,
                next_attempt_at = clock_timestamp() + ? * INTERVAL '1 millisecond'
            WHERE id = ? AND claim_token = ?
            """.trimIndent(),
            errorType,
            properties.retryDelay(claim.attempts).toMillis(),
            claim.id,
            claim.token,
        ) == 1

    fun statistics(): InstanceRegisteredOutboxStatistics =
        jdbcTemplate.queryForObject(
            """
            SELECT count(*) AS pending,
                COALESCE(EXTRACT(EPOCH FROM clock_timestamp() - min(created_at)), 0) AS oldest_age_seconds
            FROM instance_registered_outbox
            """.trimIndent(),
        ) { row, _ ->
            InstanceRegisteredOutboxStatistics(
                row.getLong("pending"),
                row.getDouble("oldest_age_seconds"),
            )
        }!!
}
