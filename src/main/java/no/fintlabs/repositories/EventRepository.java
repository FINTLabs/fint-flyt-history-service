package no.fintlabs.repositories;

import no.fintlabs.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.Tuple;
import java.util.Collection;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    Collection<Event> findAllByInstanceFlowHeadersInstanceId(String instanceId);

    Collection<Event> findAllByInstanceFlowHeadersCorrelationId(String correlationId);

    Collection<Event> findAllByInstanceFlowHeadersSourceApplicationIntegrationId(String sourceApplicationIntegrationId);

    @Query(value = "SELECT e.instanceFlowHeaders.archiveCaseId" +
            " FROM Event e" +
            " WHERE e.type = no.fintlabs.model.EventType.INFO" +
            " and e.name LIKE 'case-dispatched'" +
            " and e.instanceFlowHeaders.sourceApplicationId = :sourceApplicationId" +
            " and e.instanceFlowHeaders.sourceApplicationInstanceId = :sourceApplicationInstanceId"
    )
    Optional<String> findArchiveCaseId(
            @Param(value = "sourceApplicationId") String sourceApplicationId,
            @Param(value = "sourceApplicationInstanceId") String sourceApplicationInstanceId
    );

    @Query(value = "SELECT e.instanceFlowHeaders.sourceApplicationIntegrationId, COUNT(e)" +
            " FROM Event e" +
            " WHERE e.name LIKE 'case-dispatched'" +
            " GROUP BY e.instanceFlowHeaders.sourceApplicationIntegrationId"
    )
    Collection<Tuple> findNumberOfDispatchedInstancesPerIntegrationId();

    @Query(value = "SELECT e.instanceFlowHeaders.sourceApplicationIntegrationId, COUNT(e)" +
            " FROM Event e" +
            " WHERE e.timestamp IN (" +
            "   SELECT MAX(ie.timestamp)" +
            "   FROM Event ie" +
            "   WHERE e.instanceFlowHeaders.sourceApplicationInstanceId = ie.instanceFlowHeaders.sourceApplicationInstanceId" +
            " )" +
            " AND e.type = no.fintlabs.model.EventType.ERROR" +
            " GROUP BY e.instanceFlowHeaders.sourceApplicationIntegrationId"
    )
    Collection<Tuple> findNumberOfCurrentInstanceErrorsPerIntegrationId();

    Optional<Event> findFirstByInstanceFlowHeadersInstanceIdAndNameOrderByTimestampDesc(String instanceId, String name);

}
