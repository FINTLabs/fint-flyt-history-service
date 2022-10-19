package no.fintlabs.repositories;

import no.fintlabs.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {


    interface IntegrationIdAndCount {

        String getIntegrationId();

        long getCount();
    }

    Collection<Event> findAllByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationInstanceId(
            Long sourceApplicationId,
            String sourceApplicationInstanceId
    );

    @Query(value = "SELECT e" +
            " FROM Event e" +
            " WHERE e.timestamp IN (" +
            "   SELECT MAX(ie.timestamp)" +
            "   FROM Event ie" +
            "   WHERE e.instanceFlowHeaders.sourceApplicationInstanceId = ie.instanceFlowHeaders.sourceApplicationInstanceId" +
            " )"
    )
    Collection<Event> findLatestEventPerSourceApplicationInstanceId();

    @Query(value = "SELECT e.instanceFlowHeaders.archiveInstanceId" +
            " FROM Event e" +
            " WHERE e.type = no.fintlabs.model.EventType.INFO" +
            " and e.name LIKE 'case-dispatched'" +
            " and e.instanceFlowHeaders.sourceApplicationId = :sourceApplicationId" +
            " and e.instanceFlowHeaders.sourceApplicationInstanceId = :sourceApplicationInstanceId"
    )
    Optional<String> findArchiveInstanceId(
            @Param(value = "sourceApplicationId") Long sourceApplicationId,
            @Param(value = "sourceApplicationInstanceId") String sourceApplicationInstanceId
    );

    long countEventsByNameLike(String name);

    default long countDispatchedInstances() {
        return countEventsByNameLike("case-dispatched");
    }

    @Query(value = "SELECT e.instanceFlowHeaders.sourceApplicationIntegrationId as integrationId, COUNT(e) as count" +
            " FROM Event e" +
            " WHERE e.name LIKE 'case-dispatched'" +
            " GROUP BY e.instanceFlowHeaders.sourceApplicationIntegrationId"
    )
    Collection<IntegrationIdAndCount> countDispatchedInstancesPerIntegrationId();

    @Query(value = "SELECT COUNT(e)" +
            " FROM Event e" +
            " WHERE e.timestamp IN (" +
            "   SELECT MAX(ie.timestamp)" +
            "   FROM Event ie" +
            "   WHERE e.instanceFlowHeaders.sourceApplicationInstanceId = ie.instanceFlowHeaders.sourceApplicationInstanceId" +
            " )" +
            " AND e.type = no.fintlabs.model.EventType.ERROR"
    )
    long countCurrentInstanceErrors();

    @Query(value = "SELECT e.instanceFlowHeaders.sourceApplicationIntegrationId as integrationId, COUNT(e) as count" +
            " FROM Event e" +
            " WHERE e.timestamp IN (" +
            "   SELECT MAX(ie.timestamp)" +
            "   FROM Event ie" +
            "   WHERE e.instanceFlowHeaders.sourceApplicationInstanceId = ie.instanceFlowHeaders.sourceApplicationInstanceId" +
            " )" +
            " AND e.type = no.fintlabs.model.EventType.ERROR" +
            " GROUP BY e.instanceFlowHeaders.sourceApplicationIntegrationId"
    )
    Collection<IntegrationIdAndCount> countCurrentInstanceErrorsPerIntegrationId();

    Optional<Event> findFirstByInstanceFlowHeadersInstanceIdAndNameOrderByTimestampDesc(Long instanceId, String name);

}
