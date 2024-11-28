package no.fintlabs.repositories;

import io.hypersistence.utils.hibernate.type.array.LongArrayType;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.hypersistence.utils.hibernate.type.array.internal.AbstractArrayType;
import lombok.Builder;
import lombok.Getter;
import no.fintlabs.model.Event;
import no.fintlabs.model.IntegrationStatistics;
import org.hibernate.jpa.TypedParameterValue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.function.IntFunction;

import static no.fintlabs.EventNames.INSTANCE_DISPATCHED;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    interface IntegrationIdAndCount {

        String getIntegrationId();

        long getCount();
    }

    Page<Event> findAllByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationInstanceId(
            Long sourceApplicationId,
            String sourceApplicationInstanceId,
            Pageable pageable
    );


//    @Query(value = "" +
//            "SELECT name " +
//            "FROM event " +
//            "WHERE CAST(:a AS VARCHAR) IS NULL OR integration_id = ANY(:a)",
//            nativeQuery = true
//    )
//    List<String> a(@Param("a") TypedParameterValue aArray);

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

    default <T, T2 extends AbstractArrayType> TypedParameterValue mapToArrayType(
            T2 hibernateType,
            Collection<T> values,
            IntFunction<T[]> arrayGenerator
    ) {
        return new TypedParameterValue(
                hibernateType,
                Optional.ofNullable(values)
                        .map(iids -> iids
                                .stream()
                                .filter(Objects::nonNull)
                                .toArray(arrayGenerator)
                        )
                        .orElse(null)
        );
    }

//    @Query(value = "SELECT e.* " +
//            "FROM event AS e " +
//            "INNER JOIN ( " +
//            "    SELECT source_application_instance_id, max(timestamp) AS timestampMax " +
//            "    FROM event " +
//            "    GROUP BY source_application_instance_id " +
//            ") AS eMax " +
//            "ON e.source_application_instance_id = eMax.source_application_instance_id " +
//            "AND e.timestamp = eMax.timestampMax",
//            nativeQuery = true)
//    Page<Event> findLatestEventPerSourceApplicationInstanceId(Pageable pageable);
//
//    @Query(value = "SELECT e.* " +
//            "FROM event AS e " +
//            "INNER JOIN ( " +
//            "    SELECT source_application_instance_id, max(timestamp) AS timestampMax " +
//            "    FROM event " +
//            "    WHERE name <> 'instance-deleted' " +
//            "    GROUP BY source_application_instance_id " +
//            ") AS eMax " +
//            "ON e.source_application_instance_id = eMax.source_application_instance_id " +
//            "AND e.timestamp = eMax.timestampMax " +
//            "AND e.name <> 'instance-deleted'",
//            nativeQuery = true)
//    Page<Event> findLatestEventNotDeletedPerSourceApplicationInstanceId(Pageable pageable);

    @Query(value = "SELECT e.* " +
            "FROM event AS e " +
            "INNER JOIN ( " +
            "    SELECT source_application_instance_id, MAX(timestamp) AS timestampMax " +
            "    FROM event " +
            "    WHERE source_application_id IN :sourceApplicationIds " +
            "    GROUP BY source_application_instance_id " +
            ") AS eMax " +
            "ON e.source_application_instance_id = eMax.source_application_instance_id " +
            "AND e.timestamp = eMax.timestampMax " +
            "WHERE e.source_application_id IN :sourceApplicationIds",
            nativeQuery = true)
    Page<Event> findLatestEventPerSourceApplicationInstanceIdAndSourceApplicationIdIn(
            @Param("sourceApplicationIds") List<Long> sourceApplicationIds,
            Pageable pageable);

    @Query(value = "SELECT e.source_application_integration_id AS integrationId, COUNT(DISTINCT e.source_application_instance_id) AS count " +
            "FROM event e " +
            "WHERE e.source_application_id IN :sourceApplicationIds " +
            "  AND e.name LIKE :eventName " +
            "GROUP BY e.source_application_integration_id",
            nativeQuery = true)
    Collection<Event> findDispathedInstancesPerIntegrationIdBySourceApplicationIds(
            @Param(value = "eventName") String eventName,
            @Param("sourceApplicationIds") List<Long> sourceApplicationIds
    );

    @Query(value = "SELECT e.* " +
            "FROM event AS e " +
            "INNER JOIN ( " +
            "    SELECT source_application_instance_id, MAX(timestamp) AS timestampMax " +
            "    FROM event " +
            "    WHERE source_application_id IN :sourceApplicationIds " +
            "    AND integration_id IN :integrationIds " +
            "    GROUP BY source_application_instance_id " +
            ") AS eMax " +
            "ON e.source_application_instance_id = eMax.source_application_instance_id " +
            "AND e.timestamp = eMax.timestampMax " +
            "WHERE e.source_application_id IN :sourceApplicationIds " +
            "AND e.integration_id IN :integrationIds",
            nativeQuery = true)
    Page<Event> findLatestEventPerSourceApplicationInstanceIdAndSourceApplicationIdWithSearchParamIn(
            @Param("sourceApplicationIds") List<Long> sourceApplicationIds,
            @Param("integrationIds") List<Long> integrationIds,
            Pageable pageable);

    @Query(value = "SELECT e.* " +
            "FROM event AS e " +
            "INNER JOIN ( " +
            "    SELECT source_application_instance_id, MAX(timestamp) AS timestampMax " +
            "    FROM event " +
            "    WHERE source_application_id IN :sourceApplicationIds " +
            "    AND name <> 'instance-deleted' " +
            "    GROUP BY source_application_instance_id " +
            ") AS eMax " +
            "ON e.source_application_instance_id = eMax.source_application_instance_id " +
            "AND e.timestamp = eMax.timestampMax " +
            "WHERE e.source_application_id IN :sourceApplicationIds " +
            "AND e.name <> 'instance-deleted'",
            nativeQuery = true)
    Page<Event> findLatestEventNotDeletedPerSourceApplicationInstanceIdAndSourceApplicationIdIn(
            @Param("sourceApplicationIds") List<Long> sourceApplicationIds,
            Pageable pageable);

    Page<Event> findAllByInstanceFlowHeadersSourceApplicationIdIn(
            List<Long> sourceApplicationIds,
            Pageable pageable
    );

    @Query(value = "SELECT e.instanceFlowHeaders.archiveInstanceId " +
            "FROM Event e " +
            "WHERE e.type = no.fintlabs.model.EventType.INFO " +
            "   AND e.name = :name " +
            "   AND e.instanceFlowHeaders.sourceApplicationId = :sourceApplicationId " +
            "   AND e.instanceFlowHeaders.sourceApplicationInstanceId = :sourceApplicationInstanceId"
    )
    Optional<String> selectArchiveInstanceIdBySourceApplicationIdAndSourceApplicationInstanceIdAndName(
            @Param(value = "sourceApplicationId") Long sourceApplicationId,
            @Param(value = "sourceApplicationInstanceId") String sourceApplicationInstanceId,
            @Param(value = "name") String name
    );

    default Optional<String> findArchiveInstanceId(
            Long sourceApplicationId,
            String sourceApplicationInstanceId
    ) {
        return selectArchiveInstanceIdBySourceApplicationIdAndSourceApplicationInstanceIdAndName(
                sourceApplicationId,
                sourceApplicationInstanceId,
                INSTANCE_DISPATCHED
        );
    }

//    long countEventsByNameLike(String name);
//
//    default long countDispatchedInstances() {
//        return countEventsByNameLike(INSTANCE_DISPATCHED);
//    }

    long countByNameAndInstanceFlowHeadersSourceApplicationIdIn(String name, List<Long> sourceApplicationIds);

    default long countDispatchedInstancesBySourceApplicationIds(List<Long> sourceApplicationIds) {
        return countByNameAndInstanceFlowHeadersSourceApplicationIdIn(INSTANCE_DISPATCHED, sourceApplicationIds);
    }

//    default Collection<IntegrationIdAndCount> countDispatchedInstancesPerIntegrationId() {
//        return countNamedEventsPerIntegrationId(INSTANCE_DISPATCHED);
//    }
//
//    @Query(value = "SELECT e.instanceFlowHeaders.sourceApplicationIntegrationId AS integrationId, COUNT(e) AS count " +
//            "FROM Event e " +
//            "WHERE e.name LIKE :eventName " +
//            "GROUP BY e.instanceFlowHeaders.sourceApplicationIntegrationId"
//    )
//    Collection<IntegrationIdAndCount> countNamedEventsPerIntegrationId(
//            @Param(value = "eventName") String eventName
//    );

//    default Collection<IntegrationIdAndCount> countDispatchedInstancesPerIntegrationIdBySourceApplicationIds(List<Long> sourceApplicationIds) {
//        return countNamedEventsPerIntegrationIdBySourceApplicationIds(INSTANCE_DISPATCHED, sourceApplicationIds);
//    }
//
//    @Query(value = "SELECT e.instanceFlowHeaders.sourceApplicationIntegrationId AS integrationId, COUNT(DISTINCT e.instanceFlowHeaders.sourceApplicationInstanceId) AS count " +
//            "FROM Event e " +
//            "WHERE e.name LIKE :eventName " +
//            "AND e.instanceFlowHeaders.sourceApplicationId IN :sourceApplicationIds " +
//            "GROUP BY e.instanceFlowHeaders.sourceApplicationIntegrationId"
//    )
//    Collection<IntegrationIdAndCount> countNamedEventsPerIntegrationIdBySourceApplicationIds(
//            @Param(value = "eventName") String eventName,
//            @Param(value = "sourceApplicationIds") List<Long> sourceApplicationIds
//    );

//    @Query(value = "SELECT COUNT(*) " +
//            "FROM event AS e " +
//            "INNER JOIN ( " +
//            "   SELECT source_application_instance_id, max(timestamp) AS timestampMax " +
//            "   FROM event " +
//            "   GROUP BY source_application_instance_id " +
//            ") AS eMax " +
//            "ON e.source_application_instance_id = eMax.source_application_instance_id " +
//            "   AND e.timestamp = eMax.timestampMax " +
//            "WHERE type = 'ERROR' ",
//            nativeQuery = true
//    )
//    long countCurrentInstanceErrors();

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

//    @Query(value = "SELECT source_application_integration_id AS integrationId, COUNT(*) AS count " +
//            "FROM event AS e " +
//            "INNER JOIN ( " +
//            "   SELECT source_application_instance_id, max(timestamp) AS timestampMax " +
//            "   FROM event " +
//            "   GROUP BY source_application_instance_id " +
//            ") AS eMax " +
//            "ON e.source_application_instance_id = eMax.source_application_instance_id " +
//            "   AND e.timestamp = eMax.timestampMax " +
//            "WHERE type = 'ERROR' " +
//            "GROUP BY source_application_integration_id",
//            nativeQuery = true
//    )
//    Collection<IntegrationIdAndCount> countCurrentInstanceErrorsPerIntegrationId();

//    @Query(value = "SELECT source_application_integration_id AS integrationId, COUNT(*) AS count " +
//            "FROM event AS e " +
//            "INNER JOIN ( " +
//            "   SELECT source_application_instance_id, max(timestamp) AS timestampMax " +
//            "   FROM event " +
//            "   GROUP BY source_application_instance_id " +
//            ") AS eMax " +
//            "ON e.source_application_instance_id = eMax.source_application_instance_id " +
//            "   AND e.timestamp = eMax.timestampMax " +
//            "GROUP BY source_application_integration_id",
//            nativeQuery = true
//    )
//    Collection<IntegrationIdAndCount> countAllInstancesPerIntegrationId();

    @Query(value = "SELECT source_application_integration_id AS integrationId, COUNT(*) AS count " +
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
            "AND e.source_application_id IN :sourceApplicationIds " +
            "GROUP BY source_application_integration_id",
            nativeQuery = true
    )
    Collection<IntegrationIdAndCount> countCurrentInstanceErrorsPerIntegrationIdBySourceApplicationIds(
            @Param("sourceApplicationIds") List<Long> sourceApplicationIds);

    @Query(value = "SELECT source_application_integration_id AS integrationId, COUNT(*) AS count " +
            "FROM event AS e " +
            "INNER JOIN ( " +
            "   SELECT source_application_instance_id, max(timestamp) AS timestampMax " +
            "   FROM event " +
            "   WHERE source_application_id IN :sourceApplicationIds " +
            "   GROUP BY source_application_instance_id " +
            ") AS eMax " +
            "ON e.source_application_instance_id = eMax.source_application_instance_id " +
            "   AND e.timestamp = eMax.timestampMax " +
            "WHERE e.source_application_id IN :sourceApplicationIds " +
            "GROUP BY source_application_integration_id",
            nativeQuery = true
    )
    Collection<IntegrationIdAndCount> countAllInstancesPerIntegrationIdBySourceApplicationIds(
            @Param("sourceApplicationIds") List<Long> sourceApplicationIds);

    Optional<Event> findFirstByInstanceFlowHeadersInstanceIdAndNameOrderByTimestampDesc(Long instanceId, String name);

    Optional<Event> findFirstByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationInstanceIdAndInstanceFlowHeadersSourceApplicationIntegrationIdOrderByTimestampDesc(
            Long sourceApplicationId,
            String sourceApplicationInstanceId,
            String sourceApplicationIntegrationId
    );

    @Builder
    @Getter
    public static class QueryFilter {
        private final Set<Long> sourceApplicationIds;
        private final Set<String> sourceApplicationIntegrationIds;
        private final Set<Long> integrationIds;

        private QueryFilter(
                Set<Long> sourceApplicationIds,
                Set<String> sourceApplicationIntegrationIds,
                Set<Long> integrationIds
        ) {
            this.sourceApplicationIds = sourceApplicationIds;
            this.sourceApplicationIntegrationIds = sourceApplicationIntegrationIds;
            this.integrationIds = integrationIds;
        }

        public boolean allSourceApplicationIds() {
            return Objects.isNull(sourceApplicationIds);
        }

        public boolean allSourceApplicationIntegrationIds() {
            return Objects.isNull(sourceApplicationIntegrationIds);
        }

        public boolean allIntegrationIds() {
            return Objects.isNull(integrationIds);
        }
    }

}

