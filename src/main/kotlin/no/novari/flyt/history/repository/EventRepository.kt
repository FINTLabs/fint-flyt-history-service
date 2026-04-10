package no.novari.flyt.history.repository

import no.novari.flyt.history.model.SourceApplicationAggregateInstanceId
import no.novari.flyt.history.repository.entities.EventEntity
import no.novari.flyt.history.repository.filters.EventNamesPerInstanceStatus
import no.novari.flyt.history.repository.filters.InstanceFlowSummariesQueryFilter
import no.novari.flyt.history.repository.filters.IntegrationStatisticsQueryFilter
import no.novari.flyt.history.repository.projections.InstanceFlowSummaryNativeProjection
import no.novari.flyt.history.repository.projections.InstanceFlowSummaryProjection
import no.novari.flyt.history.repository.projections.InstanceStatisticsProjection
import no.novari.flyt.history.repository.projections.IntegrationStatisticsProjection
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Suppress("ktlint:standard:max-line-length")
@Repository
interface EventRepository : JpaRepository<EventEntity, Long> {
    @Query(
        value =
            """
            SELECT count(*)
            FROM event statusEvent
            LEFT OUTER JOIN event storageEvent
            ON statusEvent.source_application_id = storageEvent.source_application_id
            AND statusEvent.source_application_integration_id = storageEvent.source_application_integration_id
            AND statusEvent.source_application_instance_id = storageEvent.source_application_instance_id
            AND storageEvent.name IN (:allInstanceStorageStatusEventNames)
            AND storageEvent.timestamp >= ALL (
                SELECT e.timestamp
                FROM event e
                WHERE e.source_application_id = storageEvent.source_application_id
                AND e.source_application_integration_id = storageEvent.source_application_integration_id
                AND e.source_application_instance_id = storageEvent.source_application_instance_id
                AND e.name IN (:allInstanceStorageStatusEventNames)
            )
            LEFT OUTER JOIN (
                SELECT  source_application_id,
                        source_application_integration_id,
                        source_application_instance_id,
                        array_agg(name) AS names,
                        array_agg(archive_instance_id) AS archiveInstanceIds
                FROM event
                GROUP BY source_application_id, source_application_integration_id, source_application_instance_id
            ) nameAndArchiveInstanceIdAgg
            ON statusEvent.source_application_id = nameAndArchiveInstanceIdAgg.source_application_id
            AND statusEvent.source_application_integration_id = nameAndArchiveInstanceIdAgg.source_application_integration_id
            AND statusEvent.source_application_instance_id = nameAndArchiveInstanceIdAgg.source_application_instance_id
            WHERE (
                (:useStatusEventNames = FALSE AND statusEvent.name IN (:allInstanceStatusEventNames))
                OR (:useStatusEventNames = TRUE AND statusEvent.name IN (:statusEventNames))
            )
            AND statusEvent.timestamp >= ALL (
                SELECT e.timestamp
                FROM event e
                WHERE e.source_application_id = statusEvent.source_application_id
                AND e.source_application_integration_id = statusEvent.source_application_integration_id
                AND e.source_application_instance_id = statusEvent.source_application_instance_id
                AND e.name IN (:allInstanceStatusEventNames)
            )
            AND (
                :useSourceApplicationIds = FALSE
                OR statusEvent.source_application_id IN (:sourceApplicationIds)
            )
            AND (
                :useSourceApplicationIntegrationIds = FALSE
                OR statusEvent.source_application_integration_id IN (:sourceApplicationIntegrationIds)
            )
            AND (
                :useSourceApplicationInstanceIds = FALSE
                OR statusEvent.source_application_instance_id IN (:sourceApplicationInstanceIds)
            )
            AND (
                :useIntegrationIds = FALSE
                OR statusEvent.integration_id IN (:integrationIds)
            )
            AND (
                :latestStatusTimestampMin IS NULL
                OR statusEvent.timestamp >= :latestStatusTimestampMin
            )
            AND (
                :latestStatusTimestampMax IS NULL
                OR statusEvent.timestamp <= :latestStatusTimestampMax
            )
            AND (
                (:useInstanceStorageStatusNames = FALSE AND :instanceStorageStatusNeverStored IS NULL)
                OR (:useInstanceStorageStatusNames = TRUE AND storageEvent.name IN (:instanceStorageStatusNames))
                OR (storageEvent.source_application_id IS NULL AND :instanceStorageStatusNeverStored = TRUE)
            )
            AND (
                :useAssociatedEventNames = FALSE
                OR nameAndArchiveInstanceIdAgg.names @> CAST(:associatedEventNamesAsSqlArrayString AS CHARACTER VARYING[])
            )
            AND (
                :useDestinationInstanceIds = FALSE
                OR nameAndArchiveInstanceIdAgg.archiveInstanceIds && CAST(:destinationInstanceIdsAsSqlArrayString AS CHARACTER VARYING[])
            )
            """,
        nativeQuery = true,
    )
    fun getInstanceFlowSummariesTotalCount(
        @Param("useSourceApplicationIds") useSourceApplicationIds: Boolean,
        @Param("sourceApplicationIds") sourceApplicationIds: Collection<Long>,
        @Param("useSourceApplicationIntegrationIds") useSourceApplicationIntegrationIds: Boolean,
        @Param("sourceApplicationIntegrationIds") sourceApplicationIntegrationIds: Collection<String>,
        @Param("useSourceApplicationInstanceIds") useSourceApplicationInstanceIds: Boolean,
        @Param("sourceApplicationInstanceIds") sourceApplicationInstanceIds: Collection<String>,
        @Param("useIntegrationIds") useIntegrationIds: Boolean,
        @Param("integrationIds") integrationIds: Collection<Long>,
        @Param("useStatusEventNames") useStatusEventNames: Boolean,
        @Param("statusEventNames") statusEventNames: Collection<String>,
        @Param("useInstanceStorageStatusNames") useInstanceStorageStatusNames: Boolean,
        @Param("instanceStorageStatusNames") instanceStorageStatusNames: Collection<String>,
        @Param("instanceStorageStatusNeverStored") instanceStorageStatusNeverStored: Boolean?,
        @Param("useAssociatedEventNames") useAssociatedEventNames: Boolean,
        @Param("associatedEventNamesAsSqlArrayString") associatedEventNamesAsSqlArrayString: String,
        @Param("useDestinationInstanceIds") useDestinationInstanceIds: Boolean,
        @Param("destinationInstanceIdsAsSqlArrayString") destinationInstanceIdsAsSqlArrayString: String,
        @Param("latestStatusTimestampMin") latestStatusTimestampMin: OffsetDateTime?,
        @Param("latestStatusTimestampMax") latestStatusTimestampMax: OffsetDateTime?,
        @Param("allInstanceStatusEventNames") allInstanceStatusEventNames: Collection<String>,
        @Param("allInstanceStorageStatusEventNames") allInstanceStorageStatusEventNames: Collection<String>,
    ): Long

    fun getInstanceFlowSummariesTotalCount(
        filter: InstanceFlowSummariesQueryFilter,
        allInstanceStatusEventNames: Collection<String>,
        allInstanceStorageStatusEventNames: Collection<String>,
    ): Long {
        val instanceStorageStatusQueryFilter = filter.instanceStorageStatusQueryFilter
        val timeQueryFilter = filter.timeQueryFilter

        return getInstanceFlowSummariesTotalCount(
            useSourceApplicationIds = !filter.sourceApplicationIds.isNullOrEmpty(),
            sourceApplicationIds = filter.sourceApplicationIds.orDummyLongs(),
            useSourceApplicationIntegrationIds = !filter.sourceApplicationIntegrationIds.isNullOrEmpty(),
            sourceApplicationIntegrationIds = filter.sourceApplicationIntegrationIds.orDummyStrings(),
            useSourceApplicationInstanceIds = !filter.sourceApplicationInstanceIds.isNullOrEmpty(),
            sourceApplicationInstanceIds = filter.sourceApplicationInstanceIds.orDummyStrings(),
            useIntegrationIds = !filter.integrationIds.isNullOrEmpty(),
            integrationIds = filter.integrationIds.orDummyLongs(),
            useStatusEventNames = !filter.statusEventNames.isNullOrEmpty(),
            statusEventNames = filter.statusEventNames.orDummyStrings(),
            useInstanceStorageStatusNames = !instanceStorageStatusQueryFilter?.instanceStorageStatusNames.isNullOrEmpty(),
            instanceStorageStatusNames = instanceStorageStatusQueryFilter?.instanceStorageStatusNames.orDummyStrings(),
            instanceStorageStatusNeverStored = instanceStorageStatusQueryFilter?.neverStored,
            useAssociatedEventNames = !filter.associatedEventNames.isNullOrEmpty(),
            associatedEventNamesAsSqlArrayString = filter.associatedEventNames.toSqlArrayStringOrDummy(),
            useDestinationInstanceIds = !filter.destinationIds.isNullOrEmpty(),
            destinationInstanceIdsAsSqlArrayString = filter.destinationIds.toSqlArrayStringOrDummy(),
            latestStatusTimestampMin = timeQueryFilter?.latestStatusTimestampMin,
            latestStatusTimestampMax = timeQueryFilter?.latestStatusTimestampMax,
            allInstanceStatusEventNames = allInstanceStatusEventNames,
            allInstanceStorageStatusEventNames = allInstanceStorageStatusEventNames,
        )
    }

    @Query(
        value =
            """
             SELECT  statusEvent.source_application_id             AS sourceApplicationId,
                     statusEvent.source_application_integration_id AS sourceApplicationIntegrationId,
                     statusEvent.source_application_instance_id    AS sourceApplicationInstanceId,
                     statusEvent.integration_id                    AS integrationId,
                     statusEvent.instance_id                       AS latestInstanceId,
                     statusEvent.timestamp                         AS latestUpdate,
                     statusEvent.name                              AS latestStatusEventName,
                     storageEvent.name                             AS latestStorageStatusEventName,
                     array_to_string(nameAndArchiveInstanceIdAgg.archiveInstanceIds, '||') AS destinationInstanceIds
             FROM event statusEvent
             LEFT OUTER JOIN event storageEvent
             ON statusEvent.source_application_id = storageEvent.source_application_id
             AND statusEvent.source_application_integration_id = storageEvent.source_application_integration_id
             AND statusEvent.source_application_instance_id = storageEvent.source_application_instance_id
             AND storageEvent.name IN (:allInstanceStorageStatusEventNames)
             AND storageEvent.timestamp >= ALL (
                 SELECT e.timestamp
                 FROM event e
                 WHERE e.source_application_id = storageEvent.source_application_id
                 AND e.source_application_integration_id = storageEvent.source_application_integration_id
                 AND e.source_application_instance_id = storageEvent.source_application_instance_id
                 AND e.name IN (:allInstanceStorageStatusEventNames)
             )
             LEFT OUTER JOIN (
                 SELECT  source_application_id,
                         source_application_integration_id,
                         source_application_instance_id,
                         array_agg(name) AS names,
                         array_agg(archive_instance_id ORDER BY timestamp DESC) AS archiveInstanceIds
                 FROM event
                 GROUP BY source_application_id, source_application_integration_id, source_application_instance_id
             ) nameAndArchiveInstanceIdAgg
             ON statusEvent.source_application_id = nameAndArchiveInstanceIdAgg.source_application_id
             AND statusEvent.source_application_integration_id = nameAndArchiveInstanceIdAgg.source_application_integration_id
             AND statusEvent.source_application_instance_id = nameAndArchiveInstanceIdAgg.source_application_instance_id
             WHERE (
                 (:useStatusEventNames = FALSE AND statusEvent.name IN (:allInstanceStatusEventNames))
                 OR (:useStatusEventNames = TRUE AND statusEvent.name IN (:statusEventNames))
             )
             AND statusEvent.timestamp >= ALL (
                 SELECT e.timestamp
                 FROM event e
                 WHERE e.source_application_id = statusEvent.source_application_id
                 AND e.source_application_integration_id = statusEvent.source_application_integration_id
                 AND e.source_application_instance_id = statusEvent.source_application_instance_id
                 AND e.name IN (:allInstanceStatusEventNames)
             )
             AND (
                 :useSourceApplicationIds = FALSE
                 OR statusEvent.source_application_id IN (:sourceApplicationIds)
             )
             AND (
                 :useSourceApplicationIntegrationIds = FALSE
                 OR statusEvent.source_application_integration_id IN (:sourceApplicationIntegrationIds)
             )
             AND (
                 :useSourceApplicationInstanceIds = FALSE
                 OR statusEvent.source_application_instance_id IN (:sourceApplicationInstanceIds)
             )
             AND (
                 :useIntegrationIds = FALSE
                 OR statusEvent.integration_id IN (:integrationIds)
             )
             AND (
                 :latestStatusTimestampMin IS NULL
                 OR statusEvent.timestamp >= :latestStatusTimestampMin
             )
             AND (
                 :latestStatusTimestampMax IS NULL
                 OR statusEvent.timestamp <= :latestStatusTimestampMax
             )
             AND (
                 (:useInstanceStorageStatusNames = FALSE AND :instanceStorageStatusNeverStored IS NULL)
                 OR (:useInstanceStorageStatusNames = TRUE AND storageEvent.name IN (:instanceStorageStatusNames))
                 OR (storageEvent.source_application_id IS NULL AND :instanceStorageStatusNeverStored = TRUE)
             )
             AND (
                 :useAssociatedEventNames = FALSE
                 OR nameAndArchiveInstanceIdAgg.names @> CAST(:associatedEventNamesAsSqlArrayString AS CHARACTER VARYING[])
             )
             AND (
                 :useDestinationInstanceIds = FALSE
                 OR nameAndArchiveInstanceIdAgg.archiveInstanceIds && CAST(:destinationInstanceIdsAsSqlArrayString AS CHARACTER VARYING[])
             )
             ORDER BY latestUpdate DESC
             """,
        nativeQuery = true,
    )
    fun getInstanceFlowSummaries(
        @Param("useSourceApplicationIds") useSourceApplicationIds: Boolean,
        @Param("sourceApplicationIds") sourceApplicationIds: Collection<Long>,
        @Param("useSourceApplicationIntegrationIds") useSourceApplicationIntegrationIds: Boolean,
        @Param("sourceApplicationIntegrationIds") sourceApplicationIntegrationIds: Collection<String>,
        @Param("useSourceApplicationInstanceIds") useSourceApplicationInstanceIds: Boolean,
        @Param("sourceApplicationInstanceIds") sourceApplicationInstanceIds: Collection<String>,
        @Param("useIntegrationIds") useIntegrationIds: Boolean,
        @Param("integrationIds") integrationIds: Collection<Long>,
        @Param("useStatusEventNames") useStatusEventNames: Boolean,
        @Param("statusEventNames") statusEventNames: Collection<String>,
        @Param("useInstanceStorageStatusNames") useInstanceStorageStatusNames: Boolean,
        @Param("instanceStorageStatusNames") instanceStorageStatusNames: Collection<String>,
        @Param("instanceStorageStatusNeverStored") instanceStorageStatusNeverStored: Boolean?,
        @Param("useAssociatedEventNames") useAssociatedEventNames: Boolean,
        @Param("associatedEventNamesAsSqlArrayString") associatedEventNamesAsSqlArrayString: String,
        @Param("useDestinationInstanceIds") useDestinationInstanceIds: Boolean,
        @Param("destinationInstanceIdsAsSqlArrayString") destinationInstanceIdsAsSqlArrayString: String,
        @Param("latestStatusTimestampMin") latestStatusTimestampMin: OffsetDateTime?,
        @Param("latestStatusTimestampMax") latestStatusTimestampMax: OffsetDateTime?,
        @Param("allInstanceStatusEventNames") allInstanceStatusEventNames: Collection<String>,
        @Param("allInstanceStorageStatusEventNames") allInstanceStorageStatusEventNames: Collection<String>,
    ): List<InstanceFlowSummaryNativeProjection>

    fun getInstanceFlowSummaries(
        filter: InstanceFlowSummariesQueryFilter,
        allInstanceStatusEventNames: Collection<String>,
        allInstanceStorageStatusEventNames: Collection<String>,
        limit: Int?,
    ): List<InstanceFlowSummaryProjection> {
        val instanceStorageStatusQueryFilter = filter.instanceStorageStatusQueryFilter
        val timeQueryFilter = filter.timeQueryFilter

        return getInstanceFlowSummaries(
            useSourceApplicationIds = !filter.sourceApplicationIds.isNullOrEmpty(),
            sourceApplicationIds = filter.sourceApplicationIds.orDummyLongs(),
            useSourceApplicationIntegrationIds = !filter.sourceApplicationIntegrationIds.isNullOrEmpty(),
            sourceApplicationIntegrationIds = filter.sourceApplicationIntegrationIds.orDummyStrings(),
            useSourceApplicationInstanceIds = !filter.sourceApplicationInstanceIds.isNullOrEmpty(),
            sourceApplicationInstanceIds = filter.sourceApplicationInstanceIds.orDummyStrings(),
            useIntegrationIds = !filter.integrationIds.isNullOrEmpty(),
            integrationIds = filter.integrationIds.orDummyLongs(),
            useStatusEventNames = !filter.statusEventNames.isNullOrEmpty(),
            statusEventNames = filter.statusEventNames.orDummyStrings(),
            useInstanceStorageStatusNames = !instanceStorageStatusQueryFilter?.instanceStorageStatusNames.isNullOrEmpty(),
            instanceStorageStatusNames = instanceStorageStatusQueryFilter?.instanceStorageStatusNames.orDummyStrings(),
            instanceStorageStatusNeverStored = instanceStorageStatusQueryFilter?.neverStored,
            useAssociatedEventNames = !filter.associatedEventNames.isNullOrEmpty(),
            associatedEventNamesAsSqlArrayString = filter.associatedEventNames.toSqlArrayStringOrDummy(),
            useDestinationInstanceIds = !filter.destinationIds.isNullOrEmpty(),
            destinationInstanceIdsAsSqlArrayString = filter.destinationIds.toSqlArrayStringOrDummy(),
            latestStatusTimestampMin = timeQueryFilter?.latestStatusTimestampMin,
            latestStatusTimestampMax = timeQueryFilter?.latestStatusTimestampMax,
            allInstanceStatusEventNames = allInstanceStatusEventNames,
            allInstanceStorageStatusEventNames = allInstanceStorageStatusEventNames,
        ).let { rows ->
            val limitedRows =
                if (limit == null) {
                    rows
                } else {
                    rows.take(limit)
                }

            limitedRows.map { nativeProjection ->
                InstanceFlowSummaryProjection
                    .builder()
                    .sourceApplicationId(nativeProjection.getSourceApplicationId())
                    .sourceApplicationIntegrationId(nativeProjection.getSourceApplicationIntegrationId())
                    .sourceApplicationInstanceId(nativeProjection.getSourceApplicationInstanceId())
                    .integrationId(nativeProjection.getIntegrationId())
                    .latestInstanceId(nativeProjection.getLatestInstanceId())
                    .latestUpdate(nativeProjection.getLatestUpdate()?.atOffset(ZoneOffset.UTC))
                    .latestStatusEventName(nativeProjection.getLatestStatusEventName())
                    .latestStorageStatusEventName(nativeProjection.getLatestStorageStatusEventName())
                    .destinationInstanceIds(
                        nativeProjection
                            .getDestinationInstanceIds()
                            ?.takeUnless(String::isBlank)
                            ?.split("||")
                            ?.distinct()
                            ?.joinToString(", "),
                    ).build()
            }
        }
    }

    @Query(
        value =
            """
            SELECT e
            FROM EventEntity e
            WHERE e.instanceFlowHeaders.sourceApplicationId = :#{#sourceApplicationAggregateInstanceId.sourceApplicationId}
            AND e.instanceFlowHeaders.sourceApplicationIntegrationId = :#{#sourceApplicationAggregateInstanceId.sourceApplicationIntegrationId}
            AND e.instanceFlowHeaders.sourceApplicationInstanceId = :#{#sourceApplicationAggregateInstanceId.sourceApplicationInstanceId}
            AND e.name in :#{#allInstanceStatusEventNames}
            AND e.timestamp >= ALL(
                SELECT e1.timestamp
                FROM EventEntity e1
                WHERE e1.instanceFlowHeaders.sourceApplicationId = e.instanceFlowHeaders.sourceApplicationId
                AND e1.instanceFlowHeaders.sourceApplicationIntegrationId = e.instanceFlowHeaders.sourceApplicationIntegrationId
                AND e1.instanceFlowHeaders.sourceApplicationInstanceId = e.instanceFlowHeaders.sourceApplicationInstanceId
                AND e1.name IN :#{#allInstanceStatusEventNames}
            )
            """,
    )
    fun findLatestStatusEventBySourceApplicationAggregateInstanceId(
        sourceApplicationAggregateInstanceId: SourceApplicationAggregateInstanceId,
        allInstanceStatusEventNames: Collection<String>,
    ): EventEntity?

    fun findAllByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationIntegrationIdAndInstanceFlowHeadersSourceApplicationInstanceId(
        sourceApplicationId: Long,
        sourceApplicationIntegrationId: String,
        sourceApplicationInstanceId: String,
        pageable: Pageable,
    ): Page<EventEntity>

    fun findFirstByInstanceFlowHeadersInstanceIdAndNameOrderByTimestampDesc(
        instanceId: Long,
        name: String,
    ): EventEntity?

    @Query(
        value =
            """
            SELECT distinctArchiveInstanceIdAndTimestamp.archive_instance_id
            FROM (
                SELECT DISTINCT (archive_instance_id), timestamp
                FROM event
                WHERE source_application_id = :sourceApplicationId
                AND (:sourceApplicationIntegrationId IS NULL OR source_application_integration_id = :sourceApplicationIntegrationId)
                AND source_application_instance_id = :sourceApplicationInstanceId
                AND archive_instance_id IS NOT NULL
            ) distinctArchiveInstanceIdAndTimestamp
            ORDER BY distinctArchiveInstanceIdAndTimestamp.timestamp DESC
            """,
        nativeQuery = true,
    )
    fun findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
        @Param("sourceApplicationId") sourceApplicationId: Long,
        @Param("sourceApplicationIntegrationId") sourceApplicationIntegrationId: String?,
        @Param("sourceApplicationInstanceId") sourceApplicationInstanceId: String,
    ): List<String>

    @Query(
        value =
            """
             SELECT COUNT(e) AS total,
                    COALESCE(SUM(CASE WHEN e.name IN :#{#eventNamesPerInstanceStatus.inProgressStatusEventNames} THEN 1 ELSE 0 END), 0)
                        AS inProgress,
                    COALESCE(SUM(CASE WHEN e.name IN :#{#eventNamesPerInstanceStatus.transferredStatusEventNames} THEN 1 ELSE 0 END), 0)
                        AS transferred,
                    COALESCE(SUM(CASE WHEN e.name IN :#{#eventNamesPerInstanceStatus.abortedStatusEventNames} THEN 1 ELSE 0 END), 0)
                        AS aborted,
                    COALESCE(SUM(CASE WHEN e.name IN :#{#eventNamesPerInstanceStatus.failedStatusEventNames} THEN 1 ELSE 0 END), 0)
                        AS failed
             FROM EventEntity e
             WHERE e.instanceFlowHeaders.sourceApplicationId IN :#{#sourceApplicationIds}
             AND e.name IN :#{#eventNamesPerInstanceStatus.allStatusEventNames}
             AND e.timestamp >= ALL(
                SELECT e1.timestamp
                FROM EventEntity e1
                WHERE e1.instanceFlowHeaders.sourceApplicationId = e.instanceFlowHeaders.sourceApplicationId
                  AND e1.instanceFlowHeaders.sourceApplicationIntegrationId = e.instanceFlowHeaders.sourceApplicationIntegrationId
                  AND e1.instanceFlowHeaders.sourceApplicationInstanceId = e.instanceFlowHeaders.sourceApplicationInstanceId
                  AND e1.name IN :#{#eventNamesPerInstanceStatus.allStatusEventNames}
            )
            """,
    )
    fun getTotalStatistics(
        sourceApplicationIds: Collection<Long>,
        eventNamesPerInstanceStatus: EventNamesPerInstanceStatus,
    ): InstanceStatisticsProjection

    @Query(
        value =
            """
             SELECT e.instanceFlowHeaders.integrationId AS integrationId,
                    COUNT(e) AS total,
                    COALESCE(SUM(CASE WHEN e.name IN :#{#eventNamesPerInstanceStatus.inProgressStatusEventNames} THEN 1 ELSE 0 END), 0)
                        AS inProgress,
                    COALESCE(SUM(CASE WHEN e.name IN :#{#eventNamesPerInstanceStatus.transferredStatusEventNames} THEN 1 ELSE 0 END), 0)
                        AS transferred,
                    COALESCE(SUM(CASE WHEN e.name IN :#{#eventNamesPerInstanceStatus.abortedStatusEventNames} THEN 1 ELSE 0 END), 0)
                        AS aborted,
                    COALESCE(SUM(CASE WHEN e.name IN :#{#eventNamesPerInstanceStatus.failedStatusEventNames} THEN 1 ELSE 0 END), 0)
                        AS failed
             FROM EventEntity e
             WHERE (:#{#sourceApplicationIds.empty} IS TRUE
                 OR e.instanceFlowHeaders.sourceApplicationId IN :#{#sourceApplicationIds})
             AND (:#{#sourceApplicationIntegrationIds.empty} IS TRUE
                 OR e.instanceFlowHeaders.sourceApplicationIntegrationId IN :#{#sourceApplicationIntegrationIds})
             AND (:#{#integrationIds.empty} IS TRUE
                 OR e.instanceFlowHeaders.integrationId IN :#{#integrationIds})
             AND e.name IN :#{#eventNamesPerInstanceStatus.allStatusEventNames}
             AND e.timestamp >= ALL(
                SELECT e1.timestamp
                FROM EventEntity e1
                WHERE e1.instanceFlowHeaders.sourceApplicationId = e.instanceFlowHeaders.sourceApplicationId
                  AND e1.instanceFlowHeaders.sourceApplicationIntegrationId = e.instanceFlowHeaders.sourceApplicationIntegrationId
                  AND e1.instanceFlowHeaders.sourceApplicationInstanceId = e.instanceFlowHeaders.sourceApplicationInstanceId
                  AND e1.name IN :#{#eventNamesPerInstanceStatus.allStatusEventNames}
             )
             GROUP BY e.instanceFlowHeaders.integrationId
            """,
    )
    fun getIntegrationStatistics(
        sourceApplicationIds: Collection<Long>,
        sourceApplicationIntegrationIds: Collection<String>,
        integrationIds: Collection<Long>,
        eventNamesPerInstanceStatus: EventNamesPerInstanceStatus,
        pageable: Pageable,
    ): Slice<IntegrationStatisticsProjection>

    fun getIntegrationStatistics(
        integrationStatisticsQueryFilter: IntegrationStatisticsQueryFilter?,
        eventNamesPerInstanceStatus: EventNamesPerInstanceStatus,
        pageable: Pageable,
    ): Slice<IntegrationStatisticsProjection> {
        if (integrationStatisticsQueryFilter == null) {
            return getIntegrationStatistics(
                sourceApplicationIds = emptyList(),
                sourceApplicationIntegrationIds = emptyList(),
                integrationIds = emptyList(),
                eventNamesPerInstanceStatus = eventNamesPerInstanceStatus,
                pageable = pageable,
            )
        }

        return getIntegrationStatistics(
            sourceApplicationIds = integrationStatisticsQueryFilter.sourceApplicationIds.orEmpty(),
            sourceApplicationIntegrationIds = integrationStatisticsQueryFilter.sourceApplicationIntegrationIds.orEmpty(),
            integrationIds = integrationStatisticsQueryFilter.integrationIds.orEmpty(),
            eventNamesPerInstanceStatus = eventNamesPerInstanceStatus,
            pageable = pageable,
        )
    }

    private fun Collection<Long>?.orDummyLongs(): Collection<Long> =
        this?.takeUnless { it.isEmpty() } ?: listOf(Long.MIN_VALUE)

    private fun Collection<String>?.orDummyStrings(): Collection<String> =
        this?.takeUnless { it.isEmpty() } ?: listOf("__NO_MATCH__")

    private fun Collection<String>?.toSqlArrayStringOrDummy(): String =
        this?.takeUnless { it.isEmpty() }?.joinToString(prefix = "{", postfix = "}") ?: "{__NO_MATCH__}"
}
