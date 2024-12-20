package no.fintlabs;

import no.fintlabs.model.Event;
import no.fintlabs.model.SourceApplicationAggregateInstanceId;
import no.fintlabs.model.entities.EventEntity;
import no.fintlabs.model.entities.InstanceFlowHeadersEmbeddable;
import no.fintlabs.model.eventinfo.InstanceStatusEvent;
import no.fintlabs.model.eventinfo.InstanceStorageStatusEvent;
import no.fintlabs.model.instance.InstanceInfo;
import no.fintlabs.model.instance.InstanceStatus;
import no.fintlabs.model.instance.InstanceStatusFilter;
import no.fintlabs.model.statistics.InstanceStatistics;
import no.fintlabs.model.statistics.IntegrationStatistics;
import no.fintlabs.model.statistics.IntegrationStatisticsFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;


@Repository
public interface EventRepository extends JpaRepository<EventEntity, Long> {

    default Slice<InstanceInfo> getInstanceInfo(
            @Param("filter") InstanceStatusFilter filter,
            Pageable pageable
    ) {
        return getInstanceInfo(
                filter,
                InstanceStatusEvent.getAllEventNames(),
                InstanceStorageStatusEvent.getAllEventNames(),
                pageable
        );
    }

    @Query(value = """
             SELECT new no.fintlabs.model.instance.InstanceInfo(
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
                      AND e.name IN :#{#allStorageEventNames}
                )
             WHERE (
                :#{#filter.statusEventNames.empty} IS TRUE
                 OR statusEvent.name IN :#{#filter.statusEventNames.orElse(null)}
             )
             AND (
                :#{#filter.storageStatusFilter.empty} IS TRUE
                OR (
                    storageEvent.name IN :#{#filter.storageStatusFilter
                        .orElse(new no.fintlabs.model.instance.InstanceStorageStatusFilter(null, null))
                        .instanceStorageStatusNames}
                    OR (
                        storageEvent IS NULL
                        AND :#{#filter.storageStatusFilter.orElse(
                            new no.fintlabs.model.instance.InstanceStorageStatusFilter(null, null)
                        ).neverStored} IS TRUE)
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
                  AND e.name IN :#{#allStatusEventNames}
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
    Slice<InstanceInfo> getInstanceInfo(
            InstanceStatusFilter filter,
            Collection<String> allStatusEventNames,
            Collection<String> allStorageEventNames,
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

    default Optional<InstanceFlowHeadersEmbeddable> findInstanceFlowHeadersForLatestInstanceRegisteredEventWithInstanceId(Long instanceId) {
        return findInstanceFirstByInstanceFlowHeadersInstanceIdAndNameOrderByTimestampDesc(
                instanceId,
                InstanceStorageStatusEvent.INSTANCE_REGISTERED.getName()
        ).map(EventEntity::getInstanceFlowHeaders);
    }

    Optional<EventEntity> findInstanceFirstByInstanceFlowHeadersInstanceIdAndNameOrderByTimestampDesc(Long instanceId, String name);


    default Optional<String> findLatestArchiveInstanceId(
            SourceApplicationAggregateInstanceId sourceApplicationAggregateInstanceId
    ) {
        return findArchiveInstanceIdBySourceApplicationAggregateInstanceIdAndNameIn(
                sourceApplicationAggregateInstanceId.getSourceApplicationId(),
                sourceApplicationAggregateInstanceId.getSourceApplicationIntegrationId(),
                sourceApplicationAggregateInstanceId.getSourceApplicationInstanceId(),
                InstanceStatusEvent.getAllEventNames(InstanceStatus.TRANSFERRED)
        );
    }

    // TODO 12/12/2024 eivindmorch: Fix -- check commits behind main
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

    default InstanceStatistics getTotalStatistics(Collection<Long> sourceApplicationIds) {
        return getTotalStatistics(
                sourceApplicationIds,
                InstanceStatusEvent.getAllEventNames(InstanceStatus.TRANSFERRED),
                InstanceStatusEvent.getAllEventNames(InstanceStatus.IN_PROGRESS),
                InstanceStatusEvent.getAllEventNames(InstanceStatus.FAILED),
                InstanceStatusEvent.getAllEventNames()
        );
    }

    @Query(value = """
            SELECT new no.fintlabs.model.statistics.InstanceStatistics(
                    COUNT(e),
                    SUM(CASE WHEN e.name IN :#{#dispatchedEventNames} THEN 1 ELSE 0 END),
                    SUM(CASE WHEN e.name IN :#{#inProgressEventNames} THEN 1 ELSE 0 END),
                    SUM(CASE WHEN e.name IN :#{#failedEventNames} THEN 1 ELSE 0 END)
                )
                FROM EventEntity e
                WHERE e.instanceFlowHeaders.sourceApplicationId IN :#{#sourceApplicationIds}
                AND e.timestamp = (
                   SELECT MAX(e1.timestamp)
                   FROM EventEntity e1
                   WHERE e1.instanceFlowHeaders.sourceApplicationId = e.instanceFlowHeaders.sourceApplicationId
                     AND e1.instanceFlowHeaders.sourceApplicationIntegrationId = e.instanceFlowHeaders.sourceApplicationIntegrationId
                     AND e1.instanceFlowHeaders.sourceApplicationInstanceId = e.instanceFlowHeaders.sourceApplicationInstanceId
                     AND e1.name IN :#{#allStatusEventNames}
               )
            """)
    InstanceStatistics getTotalStatistics(
            Collection<Long> sourceApplicationIds,
            Collection<String> dispatchedEventNames,
            Collection<String> inProgressEventNames,
            Collection<String> failedEventNames,
            Collection<String> allStatusEventNames
    );

    default Page<IntegrationStatistics> getIntegrationStatistics(
            IntegrationStatisticsFilter filter,
            Pageable pageable
    ) {
        return getIntegrationStatistics(
                filter,
                InstanceStatusEvent.getAllEventNames(InstanceStatus.TRANSFERRED),
                InstanceStatusEvent.getAllEventNames(InstanceStatus.IN_PROGRESS),
                InstanceStatusEvent.getAllEventNames(InstanceStatus.FAILED),
                InstanceStatusEvent.getAllEventNames(),
                pageable
        );
    }

    @Query(value = """
            SELECT new no.fintlabs.model.statistics.IntegrationStatistics(
                    e.instanceFlowHeaders.integrationId,
                    COUNT(e),
                    SUM(CASE WHEN e.name IN :#{#transferredEventNames} THEN 1 ELSE 0 END),
                    SUM(CASE WHEN e.name IN :#{#inProgressEventNames} THEN 1 ELSE 0 END),
                    SUM(CASE WHEN e.name IN :#{#failedEventNames} THEN 1 ELSE 0 END)
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
            Collection<String> transferredEventNames,
            Collection<String> inProgressEventNames,
            Collection<String> failedEventNames,
            Collection<String> allStatusEventNames,
            Pageable pageable
    );

}
