package no.fintlabs.repository;

import no.fintlabs.model.SourceApplicationAggregateInstanceId;
import no.fintlabs.repository.entities.EventEntity;
import no.fintlabs.repository.filters.EventNamesPerInstanceStatus;
import no.fintlabs.repository.filters.InstanceInfoQueryFilter;
import no.fintlabs.repository.filters.IntegrationStatisticsQueryFilter;
import no.fintlabs.repository.projections.InstanceInfoProjection;
import no.fintlabs.repository.projections.InstanceStatisticsProjection;
import no.fintlabs.repository.projections.IntegrationStatisticsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;


@Repository
public interface EventRepository extends JpaRepository<EventEntity, Long> {

    @Query(value = """
             SELECT statusEvent.instanceFlowHeaders.sourceApplicationId AS sourceApplicationId,
                statusEvent.instanceFlowHeaders.sourceApplicationIntegrationId AS sourceApplicationIntegrationId,
                statusEvent.instanceFlowHeaders.sourceApplicationInstanceId AS sourceApplicationInstanceId,
                statusEvent.instanceFlowHeaders.integrationId AS integrationId,
                statusEvent.timestamp AS latesUpdate,
                statusEvent.name AS latestStatusEventName,
                storageEvent.name AS latestStorageStatusEventName,
                statusEvent.instanceFlowHeaders.archiveInstanceId AS destinationId
             FROM EventEntity statusEvent
             LEFT OUTER JOIN EventEntity storageEvent
                ON statusEvent.instanceFlowHeaders.sourceApplicationId = storageEvent.instanceFlowHeaders.sourceApplicationId
                AND statusEvent.instanceFlowHeaders.sourceApplicationIntegrationId = storageEvent.instanceFlowHeaders.sourceApplicationIntegrationId
                AND statusEvent.instanceFlowHeaders.sourceApplicationInstanceId = storageEvent.instanceFlowHeaders.sourceApplicationInstanceId
                AND storageEvent.timestamp = (
                    SELECT MAX(e.timestamp)
                    FROM EventEntity e
                    WHERE e.instanceFlowHeaders.sourceApplicationId = storageEvent.instanceFlowHeaders.sourceApplicationId
                      AND e.instanceFlowHeaders.sourceApplicationIntegrationId = storageEvent.instanceFlowHeaders.sourceApplicationIntegrationId
                      AND e.instanceFlowHeaders.sourceApplicationInstanceId = storageEvent.instanceFlowHeaders.sourceApplicationInstanceId
                      AND e.name IN :#{#allInstanceStorageStatusEventNames}
                )
             WHERE (
                :#{#filter.statusEventNames.empty} IS TRUE
                 OR statusEvent.name IN :#{#filter.statusEventNames.orElse(null)}
             )
             AND (
                :#{#filter.storageStatusFilter.empty} IS TRUE
                OR (
                    storageEvent.name IN :#{#filter.storageStatusFilter
                        .orElse(T(no.fintlabs.repository.filters.InstanceStorageStatusQueryFilter).EMPTY)
                        .instanceStorageStatusNames}
                    OR (
                        storageEvent IS NULL
                        AND :#{#filter.storageStatusFilter
                        .orElse(T(no.fintlabs.repository.filters.InstanceStorageStatusQueryFilter).EMPTY)
                        .neverStored} IS TRUE)
                )
             )
             AND (
                 :#{#filter.sourceApplicationIds.empty} IS TRUE
                 OR statusEvent.instanceFlowHeaders.sourceApplicationId IN :#{#filter.sourceApplicationIds.orElse(null)}
             )
             AND (
                 :#{#filter.sourceApplicationIntegrationIds.empty} IS TRUE
                 OR statusEvent.instanceFlowHeaders.sourceApplicationIntegrationId
                    IN :#{#filter.sourceApplicationIntegrationIds.orElse(null)}
             )
             AND (
                 :#{#filter.sourceApplicationInstanceIds.empty} IS TRUE
                 OR statusEvent.instanceFlowHeaders.sourceApplicationInstanceId
                    IN :#{#filter.sourceApplicationInstanceIds.orElse(null)}
             )
             AND (
                 :#{#filter.integrationIds.empty} IS TRUE
                 OR statusEvent.instanceFlowHeaders.integrationId IN :#{#filter.integrationIds.orElse(null)}
             )
             AND (
                 :#{#filter.destinationIds.empty} IS TRUE
                 OR statusEvent.instanceFlowHeaders.archiveInstanceId IN :#{#filter.destinationIds.orElse(null)}
             )
             AND (
                 :#{#filter.latestStatusTimestampMin.empty} IS TRUE
                 OR statusEvent.timestamp >= :#{#filter.latestStatusTimestampMin.orElse(null)}
             )
             AND (
                 :#{#filter.latestStatusTimestampMax.empty} IS TRUE
                 OR statusEvent.timestamp <= :#{#filter.latestStatusTimestampMax.orElse(null)}
             )
             AND statusEvent.timestamp = (
                SELECT MAX(e.timestamp)
                FROM EventEntity e
                WHERE e.instanceFlowHeaders.sourceApplicationId = statusEvent.instanceFlowHeaders.sourceApplicationId
                  AND e.instanceFlowHeaders.sourceApplicationIntegrationId = statusEvent.instanceFlowHeaders.sourceApplicationIntegrationId
                  AND e.instanceFlowHeaders.sourceApplicationInstanceId = statusEvent.instanceFlowHeaders.sourceApplicationInstanceId
                  AND e.name IN :#{#allInstanceStatusEventNames}
             )
             AND (
                 :#{#filter.associatedEventNames.empty} IS TRUE
                 OR (
                     SELECT COUNT(e)
                         FROM EventEntity e
                         WHERE (e = statusEvent OR (e.instanceFlowHeaders.sourceApplicationId = statusEvent.instanceFlowHeaders.sourceApplicationId
                         AND e.instanceFlowHeaders.sourceApplicationIntegrationId = statusEvent.instanceFlowHeaders.sourceApplicationIntegrationId
                         AND e.instanceFlowHeaders.sourceApplicationInstanceId = statusEvent.instanceFlowHeaders.sourceApplicationInstanceId))
                         AND e.name IN :#{#filter.associatedEventNames.orElse(null)}
                 ) > 0
            )""")
    Slice<InstanceInfoProjection> getInstanceInfo(
            InstanceInfoQueryFilter filter,
            Collection<String> allInstanceStatusEventNames,
            Collection<String> allInstanceStorageStatusEventNames,
            Pageable pageable
    );

    @Query(value = """
            SELECT e
            FROM EventEntity e
            WHERE e.instanceFlowHeaders.sourceApplicationId = :#{#sourceApplicationAggregateInstanceId.sourceApplicationId}
            AND e.instanceFlowHeaders.sourceApplicationIntegrationId = :#{#sourceApplicationAggregateInstanceId.sourceApplicationIntegrationId}
            AND e.instanceFlowHeaders.sourceApplicationInstanceId = :#{#sourceApplicationAggregateInstanceId.sourceApplicationInstanceId}
            AND e.timestamp = (
                SELECT MAX(e1.timestamp)
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

    // TODO 12/12/2024 eivindmorch: Send error message when duplicates? Should me manually unlinked from destination
    //      ids until only one remains. Unsafe to assume last is the valid one.
    //  Requires adding of replyErrorChecker in replyingKafkaTemplate and cathing the produced error
    //      Wrap this as default behaviour of FINT Kafka RequestProducer?
    @Query(value = """
            SELECT e.instanceFlowHeaders.archiveInstanceId
            FROM EventEntity e
            WHERE e.type = no.fintlabs.model.event.EventType.INFO
            AND e.instanceFlowHeaders.sourceApplicationId = :#{#sourceApplicationId}
            AND e.instanceFlowHeaders.sourceApplicationIntegrationId = :#{#sourceApplicationIntegrationId}
            AND e.instanceFlowHeaders.sourceApplicationInstanceId = :#{#sourceApplicationInstanceId}
            AND e.name IN :#{#eventNamesPerInstanceStatus.transferredStatusEventNames}
            ORDER BY e.timestamp DESC
            """)
    List<String> findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
            Long sourceApplicationId,
            String sourceApplicationIntegrationId,
            String sourceApplicationInstanceId,
            EventNamesPerInstanceStatus eventNamesPerInstanceStatus
    );

    @Query(value = """
             SELECT COUNT(e) AS total,
                    SUM(CASE WHEN e.name IN :#{#eventNamesPerInstanceStatus.inProgressStatusEventNames} THEN 1 ELSE 0 END)
                        AS inProgress,
                    SUM(CASE WHEN e.name IN :#{#eventNamesPerInstanceStatus.transferredStatusEventNames} THEN 1 ELSE 0 END)
                        AS transferred,
                    SUM(CASE WHEN e.name IN :#{#eventNamesPerInstanceStatus.abortedStatusEventNames} THEN 1 ELSE 0 END)
                        AS aborted,
                    SUM(CASE WHEN e.name IN :#{#eventNamesPerInstanceStatus.failedStatusEventNames} THEN 1 ELSE 0 END)
                        AS failed
             FROM EventEntity e
             WHERE e.instanceFlowHeaders.sourceApplicationId IN :#{#sourceApplicationIds}
             AND e.timestamp = (
                SELECT MAX(e1.timestamp)
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
                    SUM(CASE WHEN e.name IN :#{#eventNamesPerInstanceStatus.inProgressStatusEventNames} THEN 1 ELSE 0 END)
                        AS inProgress,
                    SUM(CASE WHEN e.name IN :#{#eventNamesPerInstanceStatus.transferredStatusEventNames} THEN 1 ELSE 0 END)
                        AS transferred,
                    SUM(CASE WHEN e.name IN :#{#eventNamesPerInstanceStatus.abortedStatusEventNames} THEN 1 ELSE 0 END)
                        AS aborted,
                    SUM(CASE WHEN e.name IN :#{#eventNamesPerInstanceStatus.failedStatusEventNames} THEN 1 ELSE 0 END)
                        AS failed
             FROM EventEntity e
             WHERE (:#{#integrationStatisticsQueryFilter.sourceApplicationIds.empty} IS TRUE
                 OR e.instanceFlowHeaders.sourceApplicationId IN :#{#integrationStatisticsQueryFilter.sourceApplicationIds.orElse(null)})
             AND (:#{#integrationStatisticsQueryFilter.sourceApplicationIntegrationIds.empty} IS TRUE
                 OR e.instanceFlowHeaders.sourceApplicationIntegrationId IN :#{#integrationStatisticsQueryFilter.sourceApplicationIntegrationIds.orElse(null)})
             AND (:#{#integrationStatisticsQueryFilter.integrationIds.empty} IS TRUE
                 OR e.instanceFlowHeaders.integrationId IN :#{#integrationStatisticsQueryFilter.integrationIds.orElse(null)})
             AND e.timestamp = (
                SELECT MAX(e1.timestamp)
                FROM EventEntity e1
                WHERE e1.instanceFlowHeaders.sourceApplicationId = e.instanceFlowHeaders.sourceApplicationId
                  AND e1.instanceFlowHeaders.sourceApplicationIntegrationId = e.instanceFlowHeaders.sourceApplicationIntegrationId
                  AND e1.instanceFlowHeaders.sourceApplicationInstanceId = e.instanceFlowHeaders.sourceApplicationInstanceId
                  AND e1.name IN :#{#eventNamesPerInstanceStatus.allStatusEventNames}
             )
             GROUP BY e.instanceFlowHeaders.integrationId
            """)
    Slice<IntegrationStatisticsProjection> getIntegrationStatistics(
            IntegrationStatisticsQueryFilter integrationStatisticsQueryFilter,
            EventNamesPerInstanceStatus eventNamesPerInstanceStatus,
            Pageable pageable
    );

}
