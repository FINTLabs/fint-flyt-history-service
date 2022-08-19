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

    Collection<Event> findAllByInstanceFlowHeadersInstanceId(String instanceId);

    Collection<Event> findAllByInstanceFlowHeadersCorrelationId(String correlationId);

    Collection<Event> findAllByInstanceFlowHeadersSourceApplicationIntegrationId(String sourceApplicationIntegrationId);

    @Query(value = "SELECT e.instanceFlowHeaders.archiveCaseFolderId " +
            "FROM Event e " +
            "WHERE e.type = no.fintlabs.model.EventType.INFO" +
            " and e.name LIKE 'case-dispatched'" +
            " and e.instanceFlowHeaders.sourceApplication = :sourceApplication" +
            " and e.instanceFlowHeaders.sourceApplicationInstanceId = :sourceApplicationInstanceId"
    )
    Optional<String> findArchiveCaseFolderId(
            @Param(value = "sourceApplication") String sourceApplication,
            @Param(value = "sourceApplicationInstanceId") String sourceApplicationInstanceId
    );

}
