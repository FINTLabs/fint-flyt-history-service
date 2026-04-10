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
            AND storageEvent.name IN :allInstanceStorageStatusEventNames
            AND storageEvent.timestamp >= ALL (
                SELECT e.timestamp
                FROM event e
                WHERE e.source_application_id = storageEvent.source_application_id
                AND e.source_application_integration_id = storageEvent.source_application_integration_id
                AND e.source_application_instance_id = storageEvent.source_application_instance_id
                AND e.name IN :allInstanceStorageStatusEventNames
            )
            LEFT OUTER JOIN (
                SELECT  source_application_id,
                        source_application_integration_id,
                        source_application_instance_id,
                        array_agg(name) AS names,
                        array_agg(archive_instance_id) as archiveInstanceIds
                FROM event
                GROUP BY source_application_id, source_application_integration_id, source_application_instance_id
            ) nameAndArchiveInstanceIdAgg
            ON statusEvent.source_application_id = nameAndArchiveInstanceIdAgg.source_application_id
            AND statusEvent.source_application_integration_id = nameAndArchiveInstanceIdAgg.source_application_integration_id
            AND statusEvent.source_application_instance_id = nameAndArchiveInstanceIdAgg.source_application_instance_id
            WHERE (
                (:statusEventNames IS NULL AND statusEvent.name IN :allInstanceStatusEventNames)
                OR (statusEvent.name IS NOT NULL AND statusEvent.name IN :statusEventNames)
            )
            AND statusEvent.timestamp >= ALL (
                SELECT e.timestamp
                FROM event e
                WHERE e.source_application_id = statusEvent.source_application_id
                AND e.source_application_integration_id = statusEvent.source_application_integration_id
                AND e.source_application_instance_id = statusEvent.source_application_instance_id
                AND e.name IN :allInstanceStatusEventNames
            )
            AND (
                :sourceApplicationIds IS NULL
                OR statusEvent.source_application_id IN :sourceApplicationIds
            )
            AND (
                :sourceApplicationIntegrationIds IS NULL
                OR statusEvent.source_application_integration_id IN :sourceApplicationIntegrationIds
            )
            AND (
                :sourceApplicationInstanceIds IS NULL
                OR statusEvent.source_application_instance_id IN :sourceApplicationInstanceIds
            )
            AND (
                :integrationIds IS NULL
                OR statusEvent.integration_id IN :integrationIds
            )
            AND (
                CAST(:latestStatusTimestampMin AS TIMESTAMP) IS NULL
                OR statusEvent.timestamp >= CAST(:latestStatusTimestampMin AS TIMESTAMP WITH TIME ZONE)
            )
            AND (
                CAST(:latestStatusTimestampMax AS TIMESTAMP) IS NULL
                OR statusEvent.timestamp <= CAST(:latestStatusTimestampMax AS TIMESTAMP WITH TIME ZONE)
            )
            AND (
                (:instanceStorageStatusNames IS NULL AND :instanceStorageStatusNeverStored IS NULL)
                OR storageEvent.name IN :instanceStorageStatusNames
                OR (storageEvent IS NULL AND :instanceStorageStatusNeverStored IS TRUE)
            )
            AND (
                :associatedEventNamesAsSqlArrayString IS NULL
                OR nameAndArchiveInstanceIdAgg.names @> CAST(:associatedEventNamesAsSqlArrayString AS CHARACTER VARYING[])
            )
            AND (
                :destinationInstanceIdsAsSqlArrayString IS NULL
                OR nameAndArchiveInstanceIdAgg.archiveInstanceIds && CAST(:destinationInstanceIdsAsSqlArrayString AS CHARACTER VARYING[])
            )""",
        nativeQuery = true,
    )
    fun getInstanceFlowSummariesTotalCount(
        @Param("sourceApplicationIds") sourceApplicationIds: Collection<Long>?,
        @Param("sourceApplicationIntegrationIds") sourceApplicationIntegrationIds: Collection<String>?,
        @Param("sourceApplicationInstanceIds") sourceApplicationInstanceIds: Collection<String>?,
        @Param("integrationIds") integrationIds: Collection<Long>?,
        @Param("statusEventNames") statusEventNames: Collection<String>?,
        @Param("instanceStorageStatusNames") instanceStorageStatusNames: Collection<String>?,
        @Param("instanceStorageStatusNeverStored") instanceStorageStatusNeverStored: Boolean?,
        @Param("associatedEventNamesAsSqlArrayString") associatedEventNamesAsSqlArrayString: String?,
        @Param("destinationInstanceIdsAsSqlArrayString") destinationInstanceIdsAsSqlArrayString: String?,
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
            sourceApplicationIds = filter.sourceApplicationIds.nullIfEmpty(),
            sourceApplicationIntegrationIds = filter.sourceApplicationIntegrationIds.nullIfEmpty(),
            sourceApplicationInstanceIds = filter.sourceApplicationInstanceIds.nullIfEmpty(),
            integrationIds = filter.integrationIds.nullIfEmpty(),
            statusEventNames = filter.statusEventNames.nullIfEmpty(),
            instanceStorageStatusNames = instanceStorageStatusQueryFilter?.instanceStorageStatusNames.nullIfEmpty(),
            instanceStorageStatusNeverStored = instanceStorageStatusQueryFilter?.neverStored,
            associatedEventNamesAsSqlArrayString = filter.associatedEventNames.toSqlArrayString(),
            destinationInstanceIdsAsSqlArrayString = filter.destinationIds.toSqlArrayString(),
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
                     array_to_string(nameAndArchiveInstanceIdAgg.archiveInstanceIds, '||')  AS destinationInstanceIds
             FROM event statusEvent
             LEFT OUTER JOIN event storageEvent
             ON statusEvent.source_application_id = storageEvent.source_application_id
             AND statusEvent.source_application_integration_id = storageEvent.source_application_integration_id
             AND statusEvent.source_application_instance_id = storageEvent.source_application_instance_id
             AND storageEvent.name IN :allInstanceStorageStatusEventNames
             AND storageEvent.timestamp >= ALL (
                 SELECT e.timestamp
                 FROM event e
                 WHERE e.source_application_id = storageEvent.source_application_id
                 AND e.source_application_integration_id = storageEvent.source_application_integration_id
                 AND e.source_application_instance_id = storageEvent.source_application_instance_id
                 AND e.name IN :allInstanceStorageStatusEventNames
             )
             LEFT OUTER JOIN (
                 SELECT  source_application_id,
                         source_application_integration_id,
                         source_application_instance_id,
                         array_agg(name) AS names,
                         array_agg(archive_instance_id ORDER BY timestamp DESC) as archiveInstanceIds
                 FROM event
                 GROUP BY source_application_id, source_application_integration_id, source_application_instance_id
             ) nameAndArchiveInstanceIdAgg
             ON statusEvent.source_application_id = nameAndArchiveInstanceIdAgg.source_application_id
             AND statusEvent.source_application_integration_id = nameAndArchiveInstanceIdAgg.source_application_integration_id
             AND statusEvent.source_application_instance_id = nameAndArchiveInstanceIdAgg.source_application_instance_id
             WHERE (
                 (:statusEventNames IS NULL AND statusEvent.name IN :allInstanceStatusEventNames)
                 OR (statusEvent.name IS NOT NULL AND statusEvent.name IN :statusEventNames)
             )
             AND statusEvent.timestamp >= ALL (
                 SELECT e.timestamp
                 FROM event e
                 WHERE e.source_application_id = statusEvent.source_application_id
                 AND e.source_application_integration_id = statusEvent.source_application_integration_id
                 AND e.source_application_instance_id = statusEvent.source_application_instance_id
                 AND e.name IN :allInstanceStatusEventNames
             )
             AND (
                 :sourceApplicationIds IS NULL
                 OR statusEvent.source_application_id IN :sourceApplicationIds
             )
             AND (
                 :sourceApplicationIntegrationIds IS NULL
                 OR statusEvent.source_application_integration_id IN :sourceApplicationIntegrationIds
             )
             AND (
                 :sourceApplicationInstanceIds IS NULL
                 OR statusEvent.source_application_instance_id IN :sourceApplicationInstanceIds
             )
             AND (
                 :integrationIds IS NULL
                 OR statusEvent.integration_id IN :integrationIds
             )
             AND (
                 CAST(:latestStatusTimestampMin AS TIMESTAMP) IS NULL
                 OR statusEvent.timestamp >= CAST(:latestStatusTimestampMin AS TIMESTAMP WITH TIME ZONE)
             )
             AND (
                 CAST(:latestStatusTimestampMax AS TIMESTAMP) IS NULL
                 OR statusEvent.timestamp <= CAST(:latestStatusTimestampMax AS TIMESTAMP WITH TIME ZONE)
             )
             AND (
                 (:instanceStorageStatusNames IS NULL AND :instanceStorageStatusNeverStored IS NULL)
                 OR storageEvent.name IN :instanceStorageStatusNames
                 OR (storageEvent IS NULL AND :instanceStorageStatusNeverStored IS TRUE)
             )
            AND (
                 :associatedEventNamesAsSqlArrayString IS NULL
                 OR nameAndArchiveInstanceIdAgg.names @> CAST(:associatedEventNamesAsSqlArrayString AS CHARACTER VARYING[])
             )
             AND (
                 :destinationInstanceIdsAsSqlArrayString IS NULL
                 OR nameAndArchiveInstanceIdAgg.archiveInstanceIds && CAST(:destinationInstanceIdsAsSqlArrayString AS CHARACTER VARYING[])
             )
             ORDER BY latestUpdate DESC
             LIMIT :limit""",
        nativeQuery = true,
    )
    fun getInstanceFlowSummaries(
        @Param("sourceApplicationIds") sourceApplicationIds: Collection<Long>?,
        @Param("sourceApplicationIntegrationIds") sourceApplicationIntegrationIds: Collection<String>?,
        @Param("sourceApplicationInstanceIds") sourceApplicationInstanceIds: Collection<String>?,
        @Param("integrationIds") integrationIds: Collection<Long>?,
        @Param("statusEventNames") statusEventNames: Collection<String>?,
        @Param("instanceStorageStatusNames") instanceStorageStatusNames: Collection<String>?,
        @Param("instanceStorageStatusNeverStored") instanceStorageStatusNeverStored: Boolean?,
        @Param("associatedEventNamesAsSqlArrayString") associatedEventNamesAsSqlArrayString: String?,
        @Param("destinationInstanceIdsAsSqlArrayString") destinationInstanceIdsAsSqlArrayString: String?,
        @Param("latestStatusTimestampMin") latestStatusTimestampMin: OffsetDateTime?,
        @Param("latestStatusTimestampMax") latestStatusTimestampMax: OffsetDateTime?,
        @Param("allInstanceStatusEventNames") allInstanceStatusEventNames: Collection<String>,
        @Param("allInstanceStorageStatusEventNames") allInstanceStorageStatusEventNames: Collection<String>,
        @Param("limit") limit: Int?,
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
            sourceApplicationIds = filter.sourceApplicationIds.nullIfEmpty(),
            sourceApplicationIntegrationIds = filter.sourceApplicationIntegrationIds.nullIfEmpty(),
            sourceApplicationInstanceIds = filter.sourceApplicationInstanceIds.nullIfEmpty(),
            integrationIds = filter.integrationIds.nullIfEmpty(),
            statusEventNames = filter.statusEventNames.nullIfEmpty(),
            instanceStorageStatusNames = instanceStorageStatusQueryFilter?.instanceStorageStatusNames.nullIfEmpty(),
            instanceStorageStatusNeverStored = instanceStorageStatusQueryFilter?.neverStored,
            associatedEventNamesAsSqlArrayString = filter.associatedEventNames.toSqlArrayString(),
            destinationInstanceIdsAsSqlArrayString = filter.destinationIds.toSqlArrayString(),
            latestStatusTimestampMin = timeQueryFilter?.latestStatusTimestampMin,
            latestStatusTimestampMax = timeQueryFilter?.latestStatusTimestampMax,
            allInstanceStatusEventNames = allInstanceStatusEventNames,
            allInstanceStorageStatusEventNames = allInstanceStorageStatusEventNames,
            limit = limit,
        ).map { nativeProjection ->
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

    private fun <T> Collection<T>?.nullIfEmpty(): Collection<T>? = this?.takeUnless { it.isEmpty() }

    private fun Collection<String>?.toSqlArrayString(): String? = this?.joinToString(prefix = "{", postfix = "}")
}
