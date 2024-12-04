package no.fintlabs.repositories;

import io.hypersistence.utils.hibernate.type.array.LongArrayType;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import no.fintlabs.model.Event;
import no.fintlabs.model.IntegrationStatistics;
import no.fintlabs.model.SourceApplicationAggregateInstanceId;
import org.hibernate.jpa.TypedParameterValue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static no.fintlabs.EventNames.INSTANCE_DISPATCHED;
import static no.fintlabs.repositories.HibernateTypeUtils.mapToArrayType;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    default Page<Event> getAllBySourceApplicationAggregateInstanceId(
            Long sourceApplicationId,
            String sourceApplicationIntegrationId,
            String sourceApplicationInstanceId,
            Pageable pageable
    ) {
        return findAllByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationIntegrationIdAndInstanceFlowHeadersSourceApplicationInstanceId(
                sourceApplicationId, sourceApplicationIntegrationId, sourceApplicationInstanceId, pageable
        );
    }

    Page<Event> findAllByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationIntegrationIdAndInstanceFlowHeadersSourceApplicationInstanceId(
            Long sourceApplicationId,
            String sourceApplicationIntegrationId,
            String sourceApplicationInstanceId,
            Pageable pageable
    );

    Optional<Event> findFirstByInstanceFlowHeadersInstanceIdAndNameOrderByTimestampDesc(Long instanceId, String name);

    @Query(value = "SELECT e.instanceFlowHeaders.archiveInstanceId " +
            "FROM Event e " +
            "WHERE e.type = no.fintlabs.model.EventType.INFO " +
            "   AND e.instanceFlowHeaders.sourceApplicationId = :sourceApplicationId " +
            "   AND e.instanceFlowHeaders.sourceApplicationIntegrationId = :sourceApplicationIntegrationId " +
            "   AND e.instanceFlowHeaders.sourceApplicationInstanceId = :sourceApplicationInstanceId" +
            "   AND e.name = :name "
    )
    Optional<String> selectArchiveInstanceIdBySourceApplicationAggregateInstanceIdAndName(
            @Param(value = "sourceApplicationId") Long sourceApplicationId,
            @Param(value = "sourceApplicationIntegrationId") String sourceApplicationIntegrationId,
            @Param(value = "sourceApplicationInstanceId") String sourceApplicationInstanceId,
            @Param(value = "name") String name
    );

    default Optional<String> findLatestArchiveInstanceId(
            SourceApplicationAggregateInstanceId sourceApplicationAggregateInstanceId
    ) {
        return selectArchiveInstanceIdBySourceApplicationAggregateInstanceIdAndName(
                sourceApplicationAggregateInstanceId.getSourceApplicationId(),
                sourceApplicationAggregateInstanceId.getSourceApplicationIntegrationId(),
                sourceApplicationAggregateInstanceId.getSourceApplicationInstanceId(),
                INSTANCE_DISPATCHED
        );
    }

    long countByNameAndInstanceFlowHeadersSourceApplicationIdIn(String name, List<Long> sourceApplicationIds);

    default long countDispatchedInstancesBySourceApplicationIds(List<Long> sourceApplicationIds) {
        return countByNameAndInstanceFlowHeadersSourceApplicationIdIn(INSTANCE_DISPATCHED, sourceApplicationIds);
    }

    @Query(value = "SELECT COUNT(*) " +
            "FROM event AS e " +
            "INNER JOIN ( " +
            "   SELECT source_application_instance_id, max(timestamp) AS timestampMax " +
            "   FROM event " +
            "   WHERE source_application_id IN :sourceApplicationIds " +
            "   GROUP BY source_application_instance_id " +
            ") AS eMax " +
            "ON e.source_application_instance_id = eMax.source_application_instance_id " +
            "   AND e.timestamp = eMax.timestampMax " +
            "WHERE e.type = 'ERROR' " +
            "AND e.source_application_id IN :sourceApplicationIds",
            nativeQuery = true
    )
    long countCurrentInstanceErrorsBySourceApplicationIds(@Param("sourceApplicationIds") List<Long> sourceApplicationIds);

    @Query(value = "WITH " +
            "   LatestStatusEventsPerSourceApplicationInstance AS (" +
            "       SELECT source_application_id, source_application_integration_id, integration_id, name AS lastStatusEventName " +
            "       FROM( " +
            "           SELECT source_application_id, source_application_integration_id, integration_id, name, ROW_NUMBER() OVER (" +
            "               PARTITION BY source_application_id, source_application_integration_id, source_application_instance_id " +
            "           ORDER BY timestamp DESC" +
            "           ) AS rn" +
            "           FROM event" +
            "           WHERE (name IN ('instance-received', 'instance-mapped', 'instance-ready-for-dispatch', 'instance-dispatched') OR name LIKE '%-error')" +
            "           AND (CAST(:sourceApplicationIds AS BIGINT[]) IS NULL OR source_application_id = ANY(:sourceApplicationIds)) " +
            "           AND (CAST(:sourceApplicationIntegrationIds AS VARCHAR[]) IS NULL OR source_application_integration_id = ANY(:sourceApplicationIntegrationIds)) " +
            "           AND (CAST(:integrationIds AS BIGINT[]) IS NULL OR integration_id = ANY(:integrationIds)) " +
            "       ) AS numberedStatusEvents" +
            "       WHERE rn = 1 " +
            "   ) " +
            "SELECT " +
            "   integration_id AS integrationId, " +
            "   COUNT(*) AS numberOfCurrentStatuses, " +
            "   SUM(CASE WHEN lastStatusEventName LIKE 'instance-dispatched' THEN 1 ELSE 0 END) AS numberOfCurrentDispatchedStatuses, " +
            "   SUM(CASE WHEN lastStatusEventName IN ('instance-received', 'instance-mapped', 'instance-ready-for-dispatch') THEN 1 ELSE 0 END) AS numberOfCurrentInProgressStatuses, " +
            "   SUM(CASE WHEN lastStatusEventName LIKE '%-error' THEN 1 ELSE 0 END) AS numberOfCurrentErrorStatuses " +
            "FROM LatestStatusEventsPerSourceApplicationInstance " +
            "GROUP BY integration_id ",
            countQuery =
                    "SELECT COUNT(DISTINCT (integration_id)) " +
                            "FROM event " +
                            "WHERE (CAST(:sourceApplicationIds AS BIGINT[]) IS NULL OR source_application_id = ANY(:sourceApplicationIds)) " +
                            "AND (CAST(:sourceApplicationIntegrationIds AS VARCHAR[]) IS NULL OR source_application_integration_id = ANY(:sourceApplicationIntegrationIds)) " +
                            "AND (CAST(:integrationIds AS BIGINT[]) IS NULL OR integration_id = ANY(:integrationIds)) ",
            nativeQuery = true
    )
    Page<IntegrationStatistics> getIntegrationStatisticsABC(
            @Param("sourceApplicationIds") TypedParameterValue sourceApplicationIds,
            @Param("sourceApplicationIntegrationIds") TypedParameterValue sourceApplicationIntegrationIds,
            @Param("integrationIds") TypedParameterValue integrationIds,
            Pageable pageable
    );

    default Page<IntegrationStatistics> getIntegrationStatistics(
            Collection<Long> sourceApplicationIds,
            Collection<String> sourceApplicationIntegrationIds,
            Collection<Long> integrationIds,
            Pageable pageable
    ) {
        return getIntegrationStatisticsABC(
                mapToArrayType(
                        LongArrayType.INSTANCE,
                        sourceApplicationIds,
                        Long[]::new
                ),
                mapToArrayType(
                        StringArrayType.INSTANCE,
                        sourceApplicationIntegrationIds,
                        String[]::new
                ),
                mapToArrayType(
                        LongArrayType.INSTANCE,
                        integrationIds,
                        Long[]::new
                ),
                pageable
        );
    }

}
