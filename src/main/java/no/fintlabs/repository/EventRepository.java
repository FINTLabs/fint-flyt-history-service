package no.fintlabs.repository;

import no.fintlabs.model.SourceApplicationAggregateInstanceId;
import no.fintlabs.repository.entities.EventEntity;
import no.fintlabs.repository.filters.*;
import no.fintlabs.repository.projections.InstanceFlowSummaryNativeProjection;
import no.fintlabs.repository.projections.InstanceFlowSummaryProjection;
import no.fintlabs.repository.projections.InstanceStatisticsProjection;
import no.fintlabs.repository.projections.IntegrationStatisticsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Repository
public interface EventRepository extends JpaRepository<EventEntity, Long> {

    @Query(value = """
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
                (COALESCE(:statusEventNames, null) IS NULL AND statusEvent.name IN :allInstanceStatusEventNames)
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
                COALESCE(:sourceApplicationIds, null) IS NULL
                OR statusEvent.source_application_id IN :sourceApplicationIds
            )
            AND (
                COALESCE(:sourceApplicationIntegrationIds, null) IS NULL
                OR statusEvent.source_application_integration_id IN :sourceApplicationIntegrationIds
            )
            AND (
                COALESCE(:sourceApplicationInstanceIds, null) IS NULL
                OR statusEvent.source_application_instance_id IN :sourceApplicationInstanceIds
            )
            AND (
                COALESCE(:integrationIds, null) IS NULL
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
                (COALESCE(:instanceStorageStatusNames, null) IS NULL AND :instanceStorageStatusNeverStored IS NULL)
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
            nativeQuery = true
    )
    long getInstanceFlowSummariesTotalCount(
            @Param("sourceApplicationIds") Collection<Long> sourceApplicationIds,
            @Param("sourceApplicationIntegrationIds") Collection<String> sourceApplicationIntegrationIds,
            @Param("sourceApplicationInstanceIds") Collection<String> sourceApplicationInstanceIds,
            @Param("integrationIds") Collection<Long> integrationIds,
            @Param("statusEventNames") Collection<String> statusEventNames,
            @Param("instanceStorageStatusNames") Collection<String> instanceStorageStatusNames,
            @Param("instanceStorageStatusNeverStored") Boolean instanceStorageStatusNeverStored,
            @Param("associatedEventNamesAsSqlArrayString") String associatedEventNamesAsSqlArrayString,
            @Param("destinationInstanceIdsAsSqlArrayString") String destinationInstanceIdsAsSqlArrayString,
            @Param("latestStatusTimestampMin") OffsetDateTime latestStatusTimestampMin,
            @Param("latestStatusTimestampMax") OffsetDateTime latestStatusTimestampMax,
            @Param("allInstanceStatusEventNames") Collection<String> allInstanceStatusEventNames,
            @Param("allInstanceStorageStatusEventNames") Collection<String> allInstanceStorageStatusEventNames
    );

    default long getInstanceFlowSummariesTotalCount(
            InstanceFlowSummariesQueryFilter filter,
            Collection<String> allInstanceStatusEventNames,
            Collection<String> allInstanceStorageStatusEventNames
    ) {
        String associatedEventNamesArrayString = filter.getAssociatedEventNames()
                .map(names -> names
                        .stream()
                        .collect(Collectors.joining(", ", "{", "}"))
                )
                .orElse(null);

        String destinationIdsArrayString = filter.getDestinationIds()
                .map(names -> names
                        .stream()
                        .collect(Collectors.joining(", ", "{", "}"))
                )
                .orElse(null);

        return getInstanceFlowSummariesTotalCount(
                filter.getSourceApplicationIds().orElse(List.of()),
                filter.getSourceApplicationIntegrationIds().orElse(List.of()),
                filter.getSourceApplicationInstanceIds().orElse(List.of()),
                filter.getIntegrationIds().orElse(List.of()),
                filter.getStatusEventNames().orElse(List.of()),
                filter.getInstanceStorageStatusQueryFilter()
                        .map(InstanceStorageStatusQueryFilter::getInstanceStorageStatusNames).orElse(List.of()),
                filter.getInstanceStorageStatusQueryFilter()
                        .map(InstanceStorageStatusQueryFilter::getNeverStored).orElse(null),
                associatedEventNamesArrayString,
                destinationIdsArrayString,
                filter.getTimeQueryFilter().flatMap(TimeQueryFilter::getLatestStatusTimestampMin).orElse(null),
                filter.getTimeQueryFilter().flatMap(TimeQueryFilter::getLatestStatusTimestampMax).orElse(null),
                allInstanceStatusEventNames,
                allInstanceStorageStatusEventNames
        );
    }

    @Query(value = """
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
                 (COALESCE(:statusEventNames, null) IS NULL AND statusEvent.name IN :allInstanceStatusEventNames)
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
                 COALESCE(:sourceApplicationIds, null) IS NULL
                 OR statusEvent.source_application_id IN :sourceApplicationIds
             )
             AND (
                 COALESCE(:sourceApplicationIntegrationIds, null) IS NULL
                 OR statusEvent.source_application_integration_id IN :sourceApplicationIntegrationIds
             )
             AND (
                 COALESCE(:sourceApplicationInstanceIds, null) IS NULL
                 OR statusEvent.source_application_instance_id IN :sourceApplicationInstanceIds
             )
             AND (
                 COALESCE(:integrationIds, null) IS NULL
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
                 (COALESCE(:instanceStorageStatusNames, null) IS NULL AND :instanceStorageStatusNeverStored IS NULL)
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
            nativeQuery = true
    )
    List<InstanceFlowSummaryNativeProjection> getInstanceFlowSummaries(
            @Param("sourceApplicationIds") Collection<Long> sourceApplicationIds,
            @Param("sourceApplicationIntegrationIds") Collection<String> sourceApplicationIntegrationIds,
            @Param("sourceApplicationInstanceIds") Collection<String> sourceApplicationInstanceIds,
            @Param("integrationIds") Collection<Long> integrationIds,
            @Param("statusEventNames") Collection<String> statusEventNames,
            @Param("instanceStorageStatusNames") Collection<String> instanceStorageStatusNames,
            @Param("instanceStorageStatusNeverStored") Boolean instanceStorageStatusNeverStored,
            @Param("associatedEventNamesAsSqlArrayString") String associatedEventNamesAsSqlArrayString,
            @Param("destinationInstanceIdsAsSqlArrayString") String destinationInstanceIdsAsSqlArrayString,
            @Param("latestStatusTimestampMin") OffsetDateTime latestStatusTimestampMin,
            @Param("latestStatusTimestampMax") OffsetDateTime latestStatusTimestampMax,
            @Param("allInstanceStatusEventNames") Collection<String> allInstanceStatusEventNames,
            @Param("allInstanceStorageStatusEventNames") Collection<String> allInstanceStorageStatusEventNames,
            @Param("limit") Integer limit
    );

    default List<InstanceFlowSummaryProjection> getInstanceFlowSummaries(
            InstanceFlowSummariesQueryFilter filter,
            Collection<String> allInstanceStatusEventNames,
            Collection<String> allInstanceStorageStatusEventNames,
            Integer limit
    ) {
        String associatedEventNamesArrayString = filter.getAssociatedEventNames()
                .map(names -> names
                        .stream()
                        .collect(Collectors.joining(", ", "{", "}"))
                )
                .orElse(null);

        String destinationIdsArrayString = filter.getDestinationIds()
                .map(names -> names
                        .stream()
                        .collect(Collectors.joining(", ", "{", "}"))
                )
                .orElse(null);

        return getInstanceFlowSummaries(
                filter.getSourceApplicationIds().orElse(List.of()),
                filter.getSourceApplicationIntegrationIds().orElse(List.of()),
                filter.getSourceApplicationInstanceIds().orElse(List.of()),
                filter.getIntegrationIds().orElse(List.of()),
                filter.getStatusEventNames().orElse(List.of()),
                filter.getInstanceStorageStatusQueryFilter()
                        .map(InstanceStorageStatusQueryFilter::getInstanceStorageStatusNames).orElse(List.of()),
                filter.getInstanceStorageStatusQueryFilter()
                        .map(InstanceStorageStatusQueryFilter::getNeverStored).orElse(null),
                associatedEventNamesArrayString,
                destinationIdsArrayString,
                filter.getTimeQueryFilter().flatMap(TimeQueryFilter::getLatestStatusTimestampMin).orElse(null),
                filter.getTimeQueryFilter().flatMap(TimeQueryFilter::getLatestStatusTimestampMax).orElse(null),
                allInstanceStatusEventNames,
                allInstanceStorageStatusEventNames,
                limit
        ).stream()
                .map(nativeProjection -> InstanceFlowSummaryProjection
                        .builder()
                        .sourceApplicationId(nativeProjection.getSourceApplicationId())
                        .sourceApplicationIntegrationId(nativeProjection.getSourceApplicationIntegrationId())
                        .sourceApplicationInstanceId(nativeProjection.getSourceApplicationInstanceId())
                        .integrationId(nativeProjection.getIntegrationId())
                        .latestInstanceId(nativeProjection.getLatestInstanceId())
                        .latestUpdate(
                                OffsetDateTime.from(
                                        nativeProjection.getLatestUpdate().atOffset(ZoneOffset.UTC)
                                )
                        )
                        .latestStatusEventName(nativeProjection.getLatestStatusEventName())
                        .latestStorageStatusEventName(nativeProjection.getLatestStorageStatusEventName())
                        .destinationInstanceIds(
                                Optional.ofNullable(nativeProjection.getDestinationInstanceIds())
                                        .filter(s -> !s.isBlank())
                                        .map(s -> Arrays.stream(s.split("\\|\\|"))
                                                .distinct()
                                                .collect(Collectors.joining(", "))
                                        )
                                        .orElse(null)
                        ).build()
                ).toList();
    }

    @Query(value = """
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
            """)
    Optional<EventEntity> findLatestStatusEventBySourceApplicationAggregateInstanceId(
            SourceApplicationAggregateInstanceId sourceApplicationAggregateInstanceId,
            Collection<String> allInstanceStatusEventNames
    );

    Page<EventEntity> findAllByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationIntegrationIdAndInstanceFlowHeadersSourceApplicationInstanceId(
            Long sourceApplicationId,
            String sourceApplicationIntegrationId,
            String sourceApplicationInstanceId,
            Pageable pageable
    );

    Optional<EventEntity> findFirstByInstanceFlowHeadersInstanceIdAndNameOrderByTimestampDesc(Long instanceId, String name);

    @Query(value = """
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
            nativeQuery = true)
    List<String> findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
            @Param("sourceApplicationId") Long sourceApplicationId,
            @Param("sourceApplicationIntegrationId") String sourceApplicationIntegrationId,
            @Param("sourceApplicationInstanceId") String sourceApplicationInstanceId
    );

    @Query(value = """
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
            """)
    InstanceStatisticsProjection getTotalStatistics(
            Collection<Long> sourceApplicationIds,
            EventNamesPerInstanceStatus eventNamesPerInstanceStatus
    );

    @Query(value = """
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
            """)
    Slice<IntegrationStatisticsProjection> getIntegrationStatistics(
            Collection<Long> sourceApplicationIds,
            Collection<String> sourceApplicationIntegrationIds,
            Collection<Long> integrationIds,
            EventNamesPerInstanceStatus eventNamesPerInstanceStatus,
            Pageable pageable
    );

    default Slice<IntegrationStatisticsProjection> getIntegrationStatistics(
            IntegrationStatisticsQueryFilter integrationStatisticsQueryFilter,
            EventNamesPerInstanceStatus eventNamesPerInstanceStatus,
            Pageable pageable
    ) {
        if (integrationStatisticsQueryFilter == null) {
            return getIntegrationStatistics(List.of(), List.of(), List.of(), eventNamesPerInstanceStatus, pageable);
        }
        return getIntegrationStatistics(
                integrationStatisticsQueryFilter.getSourceApplicationIds().orElse(List.of()),
                integrationStatisticsQueryFilter.getSourceApplicationIntegrationIds().orElse(List.of()),
                integrationStatisticsQueryFilter.getIntegrationIds().orElse(List.of()),
                eventNamesPerInstanceStatus,
                pageable
        );
    }

}
