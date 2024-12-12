package no.fintlabs.repositories;

import no.fintlabs.model.Event;
import no.fintlabs.model.InstanceStatus;
import no.fintlabs.model.InstanceStatusFilter;
import no.fintlabs.model.SourceApplicationAggregateInstanceId;
import no.fintlabs.model.entities.EventEntity;
import no.fintlabs.model.eventinfo.InstanceStatusEvent;
import no.fintlabs.model.eventinfo.InstanceStatusEventCategory;
import no.fintlabs.model.eventinfo.InstanceStorageStatusEvent;
import no.fintlabs.model.statistics.IntegrationStatistics;
import no.fintlabs.model.statistics.IntegrationStatisticsFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;


@Repository
public interface EventRepository extends JpaRepository<EventEntity, Long> {

    default Page<InstanceStatus> getInstanceStatuses(
            @Param("filter") InstanceStatusFilter filter,
            Pageable pageable
    ) {
        return getInstanceStatuses(
                filter,
                InstanceStatusEvent.getAllEventNames(),
                InstanceStorageStatusEvent.getAllEventNames(),
                pageable);
    }

    @Query(value = """
            SELECT new no.fintlabs.model.InstanceStatus(
               statusEvent.instanceFlowHeaders.sourceApplicationId,
               statusEvent.instanceFlowHeaders.sourceApplicationIntegrationId,
               statusEvent.instanceFlowHeaders.sourceApplicationInstanceId,
               statusEvent.instanceFlowHeaders.integrationId,
               statusEvent.timestamp,
               statusEvent.name,
               storageEvent.name,
               statusEvent.instanceFlowHeaders.archiveInstanceId
            )
            FROM EventEntity statusEvent
            INNER JOIN EventEntity storageEvent
               ON statusEvent.instanceFlowHeaders.sourceApplicationId = storageEvent.instanceFlowHeaders.sourceApplicationId
               AND statusEvent.instanceFlowHeaders.sourceApplicationIntegrationId = storageEvent.instanceFlowHeaders.sourceApplicationIntegrationId
               AND statusEvent.instanceFlowHeaders.sourceApplicationInstanceId = storageEvent.instanceFlowHeaders.sourceApplicationInstanceId
            WHERE (:#{#filter.statusEventNames.empty} IS TRUE
                    OR statusEvent.name IN :#{#filter.statusEventNames.orElse(null)})
               AND (:#{#filter.storageEventNames.empty} IS TRUE
                    OR storageEvent.name IN :#{#filter.storageEventNames.orElse(null)})
               AND (:#{#filter.sourceApplicationIds.empty} IS TRUE
                    OR statusEvent.instanceFlowHeaders.sourceApplicationId IN :#{#filter.sourceApplicationIds.orElse(null)})
               AND (:#{#filter.sourceApplicationIntegrationIds.empty} IS TRUE
                    OR statusEvent.instanceFlowHeaders.sourceApplicationIntegrationId IN :#{#filter.sourceApplicationIntegrationIds.orElse(null)})
               AND (:#{#filter.sourceApplicationInstanceIds.empty} IS TRUE
                    OR statusEvent.instanceFlowHeaders.sourceApplicationInstanceId IN :#{#filter.sourceApplicationInstanceIds.orElse(null)})
               AND (:#{#filter.integrationIds.empty} IS TRUE
                    OR statusEvent.instanceFlowHeaders.integrationId IN :#{#filter.integrationIds.orElse(null)})
               AND (:#{#filter.destinationIds.empty} IS TRUE
                    OR statusEvent.instanceFlowHeaders.archiveInstanceId IN :#{#filter.destinationIds.orElse(null)})
               AND (:#{#filter.latestStatusTimestampMin.empty} IS TRUE
                    OR statusEvent.timestamp >= :#{#filter.latestStatusTimestampMin.orElse(null)})
               AND (:#{#filter.latestStatusTimestampMax.empty} IS TRUE
                    OR statusEvent.timestamp <= :#{#filter.latestStatusTimestampMax.orElse(null)})
               AND statusEvent.timestamp = (
                   SELECT MAX(e.timestamp)
                   FROM EventEntity e
                   WHERE e.instanceFlowHeaders.sourceApplicationId = statusEvent.instanceFlowHeaders.sourceApplicationId
                     AND e.instanceFlowHeaders.sourceApplicationIntegrationId = statusEvent.instanceFlowHeaders.sourceApplicationIntegrationId
                     AND e.instanceFlowHeaders.sourceApplicationInstanceId = statusEvent.instanceFlowHeaders.sourceApplicationInstanceId
                     AND e.name IN :#{#allStatusEventNames}
               )
               AND storageEvent.timestamp = (
                   SELECT MAX(e.timestamp)
                   FROM EventEntity e
                   WHERE e.instanceFlowHeaders.sourceApplicationId = storageEvent.instanceFlowHeaders.sourceApplicationId
                     AND e.instanceFlowHeaders.sourceApplicationIntegrationId = storageEvent.instanceFlowHeaders.sourceApplicationIntegrationId
                     AND e.instanceFlowHeaders.sourceApplicationInstanceId = storageEvent.instanceFlowHeaders.sourceApplicationInstanceId
                     AND e.name IN :#{#allStorageEventNames}
               )
            """)
    Page<InstanceStatus> getInstanceStatuses(
            @Param("filter") InstanceStatusFilter filter,
            @Param("allStatusEventNames") Collection<String> allStatusEventNames,
            @Param("allStorageEventNames") Collection<String> allStorageEventNames,
            Pageable pageable
    );

    default Optional<Event> findLatestStatusEventBySourceApplicationAggregateInstanceId(
            SourceApplicationAggregateInstanceId sourceApplicationAggregateInstanceId
    ) {
        return findLatestStatusEventBySourceApplicationAggregateInstanceId(
                sourceApplicationAggregateInstanceId,
                InstanceStatusEvent.getAllEventNames()
        );
    }

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
                AND e1.name IN :#{#allStatusEventNames}
            )
            """)
    Optional<Event> findLatestStatusEventBySourceApplicationAggregateInstanceId(
            SourceApplicationAggregateInstanceId sourceApplicationAggregateInstanceId,
            Set<String> allStatusEventNames
    );

    default Page<EventEntity> getAllBySourceApplicationAggregateInstanceId(
            Long sourceApplicationId,
            String sourceApplicationIntegrationId,
            String sourceApplicationInstanceId,
            Pageable pageable
    ) {
        return findAllByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationIntegrationIdAndInstanceFlowHeadersSourceApplicationInstanceId(
                sourceApplicationId, sourceApplicationIntegrationId, sourceApplicationInstanceId, pageable
        );
    }

    Page<EventEntity> findAllByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationIntegrationIdAndInstanceFlowHeadersSourceApplicationInstanceId(
            Long sourceApplicationId,
            String sourceApplicationIntegrationId,
            String sourceApplicationInstanceId,
            Pageable pageable
    );

    Optional<EventEntity> findFirstByInstanceFlowHeadersInstanceIdAndNameInOrderByTimestampDesc(Long instanceId, Collection<String> name);

    default Optional<String> findLatestArchiveInstanceId(
            SourceApplicationAggregateInstanceId sourceApplicationAggregateInstanceId
    ) {
        return findArchiveInstanceIdBySourceApplicationAggregateInstanceIdAndNameIn(
                sourceApplicationAggregateInstanceId.getSourceApplicationId(),
                sourceApplicationAggregateInstanceId.getSourceApplicationIntegrationId(),
                sourceApplicationAggregateInstanceId.getSourceApplicationInstanceId(),
                InstanceStatusEvent.getAllEventNames(
                        InstanceStatusEventCategory.AUTOMATICALLY_DISPATCHED,
                        InstanceStatusEventCategory.MANUALLY_PROCESSED
                )
        );
    }

    // TODO 12/12/2024 eivindmorch: Fix
    @Query(value = """
            SELECT e.instanceFlowHeaders.archiveInstanceId
            FROM EventEntity e
            WHERE e.type = no.fintlabs.model.eventinfo.EventType.INFO
            AND e.instanceFlowHeaders.sourceApplicationId = :sourceApplicationId
            AND e.instanceFlowHeaders.sourceApplicationIntegrationId = :sourceApplicationIntegrationId
            AND e.instanceFlowHeaders.sourceApplicationInstanceId = :sourceApplicationInstanceId
            AND e.name = :name
            """)
    Optional<String> findArchiveInstanceIdBySourceApplicationAggregateInstanceIdAndNameIn(
            @Param(value = "sourceApplicationId") Long sourceApplicationId,
            @Param(value = "sourceApplicationIntegrationId") String sourceApplicationIntegrationId,
            @Param(value = "sourceApplicationInstanceId") String sourceApplicationInstanceId,
            @Param(value = "name") Collection<String> names
    );

    // TODO 05/12/2024 eivindmorch: Exclude storage events and duplicate dispatches from search
    default long countDispatchedInstancesBySourceApplicationIds(List<Long> sourceApplicationIds) {
        return countByNameInAndInstanceFlowHeadersSourceApplicationIdIn(
                InstanceStatusEvent.getAllEventNames(
                        InstanceStatusEventCategory.AUTOMATICALLY_DISPATCHED,
                        InstanceStatusEventCategory.MANUALLY_PROCESSED
                ),
                sourceApplicationIds
        );
    }

    long countByNameInAndInstanceFlowHeadersSourceApplicationIdIn(Collection<String> names, List<Long> sourceApplicationIds);


    // TODO 05/12/2024 eivindmorch: Exclude storage events from search, use jpql
    @Query(value = """
            SELECT COUNT(*)
            FROM event AS e
            INNER JOIN (
               SELECT source_application_instance_id, max(timestamp) AS timestampMax
               FROM event
               WHERE source_application_id IN :sourceApplicationIds
               GROUP BY source_application_instance_id
            ) AS eMax
            ON e.source_application_instance_id = eMax.source_application_instance_id
               AND e.timestamp = eMax.timestampMax
            WHERE e.type = 'ERROR'
            AND e.source_application_id IN :sourceApplicationIds
            """,
            nativeQuery = true)
    long countCurrentInstanceErrorsBySourceApplicationIds(@Param("sourceApplicationIds") List<Long> sourceApplicationIds);

//    default Page<IntegrationStatistics> getIntegrationStatistics(
//            Collection<Long> sourceApplicationIds,
//            Collection<String> sourceApplicationIntegrationIds,
//            Collection<Long> integrationIds,
//            Pageable pageable
//    ) {
//        return getIntegrationStatistics(
//                mapToArrayType(
//                        LongArrayType.INSTANCE,
//                        sourceApplicationIds,
//                        Long[]::new
//                ),
//                mapToArrayType(
//                        StringArrayType.INSTANCE,
//                        sourceApplicationIntegrationIds,
//                        String[]::new
//                ),
//                mapToArrayType(
//                        LongArrayType.INSTANCE,
//                        integrationIds,
//                        Long[]::new
//                ),
//                pageable
//        );
//    }
//
//    @Query(value = """
//            WITH LatestStatusEventsPerIntegration AS (
//                   SELECT source_application_id, source_application_integration_id, integration_id, name, type
//                   FROM(
//                       SELECT source_application_id, source_application_integration_id, integration_id, name, type, ROW_NUMBER() OVER (
//                           PARTITION BY source_application_id, source_application_integration_id, source_application_instance_id
//                       ORDER BY timestamp DESC
//                       ) AS rn
//                       FROM event
//                       WHERE (type = 'INFO' AND name IN ('instance-received', 'instance-mapped', 'instance-ready-for-dispatch', 'instance-dispatched') OR type = 'ERROR')
//                       AND (CAST(:sourceApplicationIds AS BIGINT[]) IS NULL OR source_application_id = ANY(:sourceApplicationIds))
//                       AND (CAST(:sourceApplicationIntegrationIds AS VARCHAR[]) IS NULL OR source_application_integration_id = ANY(:sourceApplicationIntegrationIds))
//                       AND (CAST(:integrationIds AS BIGINT[]) IS NULL OR integration_id = ANY(:integrationIds))
//                   ) AS numberedStatusEvents
//                   WHERE rn = 1
//               )
//            SELECT
//               integration_id AS integrationId,
//               COUNT(*) AS numberOfCurrentStatuses,
//               SUM(CASE WHEN name = 'instance-dispatched' THEN 1 ELSE 0 END) AS numberOfCurrentDispatchedStatuses,
//               SUM(CASE WHEN name IN ('instance-received', 'instance-mapped', 'instance-ready-for-dispatch') THEN 1 ELSE 0 END) AS numberOfCurrentInProgressStatuses,
//               SUM(CASE WHEN type = 'ERROR' THEN 1 ELSE 0 END) AS numberOfCurrentErrorStatuses
//            FROM LatestStatusEventsPerIntegration
//            GROUP BY integration_id
//            """,
//            countQuery = """
//                    SELECT COUNT(DISTINCT (integration_id))
//                         FROM event
//                         WHERE (CAST(:sourceApplicationIds AS BIGINT[]) IS NULL OR source_application_id = ANY(:sourceApplicationIds))
//                         AND (CAST(:sourceApplicationIntegrationIds AS VARCHAR[]) IS NULL OR source_application_integration_id = ANY(:sourceApplicationIntegrationIds))
//                         AND (CAST(:integrationIds AS BIGINT[]) IS NULL OR integration_id = ANY(:integrationIds))
//                    """,
//            nativeQuery = true
//    )
//    Page<IntegrationStatistics> getIntegrationStatistics(
//            @Param("sourceApplicationIds") TypedParameterValue sourceApplicationIds,
//            @Param("sourceApplicationIntegrationIds") TypedParameterValue sourceApplicationIntegrationIds,
//            @Param("integrationIds") TypedParameterValue integrationIds,
//            Pageable pageable
//    );

    default Page<IntegrationStatistics> getIntegrationStatistics(
            IntegrationStatisticsFilter filter,
            Pageable pageable
    ) {
        return getIntegrationStatistics(
                filter,
                InstanceStatusEvent.getAllEventNames(
                        InstanceStatusEventCategory.AUTOMATICALLY_DISPATCHED,
                        InstanceStatusEventCategory.MANUALLY_PROCESSED
                ),
                InstanceStatusEvent.getAllEventNames(InstanceStatusEventCategory.IN_PROGRESS),
                InstanceStatusEvent.getAllEventNames(),
                pageable
        );
    }

    // TODO 12/12/2024 eivindmorch: Separate manually and automatically dispatched?
    @Query(value = """
            SELECT new no.fintlabs.model.statistics.IntegrationStatistics(
                    e.instanceFlowHeaders.integrationId,
                    COUNT(e),
                    SUM(CASE WHEN e.name IN :#{#dispatchedEventNames} THEN 1 ELSE 0 END),
                    SUM(CASE WHEN e.name IN :#{#inProgressEventNames} THEN 1 ELSE 0 END),
                    SUM(CASE WHEN e.type = 'ERROR' THEN 1 ELSE 0 END)
                )
                FROM EventEntity e
                WHERE (:#{#filter.sourceApplicationIds.empty} IS TRUE
                    OR e.instanceFlowHeaders.sourceApplicationId IN :#{#filter.sourceApplicationIds.orElse(null)})
                AND (:#{#filter.sourceApplicationIntegrationIds.empty} IS TRUE
                    OR e.instanceFlowHeaders.sourceApplicationIntegrationId IN :#{#filter.sourceApplicationIntegrationIds.orElse(null)})
                AND (:#{#filter.integrationIds.empty} IS TRUE
                    OR e.instanceFlowHeaders.integrationId IN :#{#filter.integrationIds.orElse(null)})
                AND e.timestamp = (
                   SELECT MAX(e1.timestamp)
                   FROM EventEntity e1
                   WHERE e1.instanceFlowHeaders.sourceApplicationId = e.instanceFlowHeaders.sourceApplicationId
                     AND e1.instanceFlowHeaders.sourceApplicationIntegrationId = e.instanceFlowHeaders.sourceApplicationIntegrationId
                     AND e1.instanceFlowHeaders.sourceApplicationInstanceId = e.instanceFlowHeaders.sourceApplicationInstanceId
                     AND e1.name IN :#{#allStatusEventNames}
               )
               GROUP BY e.instanceFlowHeaders.integrationId
            """)
    Page<IntegrationStatistics> getIntegrationStatistics(
            IntegrationStatisticsFilter filter,
            Collection<String> dispatchedEventNames,
            Collection<String> inProgressEventNames,
            Collection<String> allStatusEventNames,
            Pageable pageable
    );

}
