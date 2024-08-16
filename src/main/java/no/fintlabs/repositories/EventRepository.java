package no.fintlabs.repositories;

import no.fintlabs.model.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static no.fintlabs.EventTopicNames.INSTANCE_DISPATCHED;

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

    @Query(value = "SELECT e.* " +
            "FROM event AS e " +
            "INNER JOIN ( " +
            "    SELECT source_application_instance_id, max(timestamp) AS timestampMax " +
            "    FROM event " +
            "    GROUP BY source_application_instance_id " +
            ") AS eMax " +
            "ON e.source_application_instance_id = eMax.source_application_instance_id " +
            "    AND e.timestamp = eMax.timestampMax",
            nativeQuery = true
    )
    Page<Event> findLatestEventPerSourceApplicationInstanceId(Pageable pageable);

    @Query(value = "SELECT e FROM Event e " +
            "WHERE e.instanceFlowHeaders.sourceApplicationId IN :sourceApplicationIds " +
            "AND e.timestamp = (SELECT MAX(e2.timestamp) " +
            "FROM Event e2 " +
            "WHERE e2.instanceFlowHeaders.sourceApplicationInstanceId = e.instanceFlowHeaders.sourceApplicationInstanceId " +
            "AND e2.instanceFlowHeaders.sourceApplicationId IN :sourceApplicationIds) ")
    Page<Event> findLatestEventPerSourceApplicationInstanceIdAndSourceApplicationIdIn(
            @Param("sourceApplicationIds") List<Long> sourceApplicationIds,
            Pageable pageable);

    Page<Event> findAllByInstanceFlowHeadersSourceApplicationIdIn(List<Long> sourceApplicationIds, Pageable pageable);

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

    long countEventsByNameLike(String name);

    default long countDispatchedInstances() {
        return countEventsByNameLike(INSTANCE_DISPATCHED);
    }

    long countByNameAndInstanceFlowHeadersSourceApplicationIdIn(String name, List<Long> sourceApplicationIds);

    default long countDispatchedInstancesBySourceApplicationIds(List<Long> sourceApplicationIds) {
        return countByNameAndInstanceFlowHeadersSourceApplicationIdIn(INSTANCE_DISPATCHED, sourceApplicationIds);
    }

    default Collection<IntegrationIdAndCount> countDispatchedInstancesPerIntegrationId() {
        return countNamedEventsPerIntegrationId(INSTANCE_DISPATCHED);
    }

    @Query(value = "SELECT e.instanceFlowHeaders.sourceApplicationIntegrationId AS integrationId, COUNT(e) AS count " +
            "FROM Event e " +
            "WHERE e.name LIKE :eventName " +
            "GROUP BY e.instanceFlowHeaders.sourceApplicationIntegrationId"
    )
    Collection<IntegrationIdAndCount> countNamedEventsPerIntegrationId(
            @Param(value = "eventName") String eventName
    );

    default Collection<IntegrationIdAndCount> countDispatchedInstancesPerIntegrationIdBySourceApplicationIds(List<Long> sourceApplicationIds) {
        return countNamedEventsPerIntegrationIdBySourceApplicationIds(INSTANCE_DISPATCHED, sourceApplicationIds);
    }

    @Query(value = "SELECT e.instanceFlowHeaders.sourceApplicationIntegrationId AS integrationId, COUNT(e) AS count " +
            "FROM Event e " +
            "WHERE e.name LIKE :eventName " +
            "AND e.instanceFlowHeaders.sourceApplicationId IN :sourceApplicationIds " +
            "GROUP BY e.instanceFlowHeaders.sourceApplicationIntegrationId"
    )
    Collection<IntegrationIdAndCount> countNamedEventsPerIntegrationIdBySourceApplicationIds(
            @Param(value = "eventName") String eventName,
            @Param(value = "sourceApplicationIds") List<Long> sourceApplicationIds
    );


    @Query(value = "SELECT COUNT(*) " +
            "FROM event AS e " +
            "INNER JOIN ( " +
            "   SELECT source_application_instance_id, max(timestamp) AS timestampMax " +
            "   FROM event " +
            "   GROUP BY source_application_instance_id " +
            ") AS eMax " +
            "ON e.source_application_instance_id = eMax.source_application_instance_id " +
            "   AND e.timestamp = eMax.timestampMax " +
            "WHERE type = 'ERROR' ",
            nativeQuery = true
    )
    long countCurrentInstanceErrors();

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

    @Query(value = "SELECT source_application_integration_id AS integrationId, COUNT(*) AS count " +
            "FROM event AS e " +
            "INNER JOIN ( " +
            "   SELECT source_application_instance_id, max(timestamp) AS timestampMax " +
            "   FROM event " +
            "   GROUP BY source_application_instance_id " +
            ") AS eMax " +
            "ON e.source_application_instance_id = eMax.source_application_instance_id " +
            "   AND e.timestamp = eMax.timestampMax " +
            "WHERE type = 'ERROR' " +
            "GROUP BY source_application_integration_id",
            nativeQuery = true
    )
    Collection<IntegrationIdAndCount> countCurrentInstanceErrorsPerIntegrationId();

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


    Optional<Event> findFirstByInstanceFlowHeadersInstanceIdAndNameOrderByTimestampDesc(Long instanceId, String name);

    Optional<Event> findFirstByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationInstanceIdAndInstanceFlowHeadersSourceApplicationIntegrationIdOrderByTimestampDesc(
            Long sourceApplicationId,
            String sourceApplicationInstanceId,
            String sourceApplicationIntegrationId
    );

}
