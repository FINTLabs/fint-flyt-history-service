package no.fintlabs;

import no.fintlabs.model.Event;
import no.fintlabs.model.IntegrationStatistics;
import no.fintlabs.repositories.EventRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

import static no.fintlabs.resourceserver.UrlPaths.INTERNAL_API;

@RestController
@RequestMapping(INTERNAL_API + "/historikk")
public class HistoryController {

    private final EventRepository eventRepository;
    private final IntegrationStatisticsService integrationStatisticsService;

    public HistoryController(EventRepository eventRepository, IntegrationStatisticsService integrationStatisticsService) {
        this.eventRepository = eventRepository;
        this.integrationStatisticsService = integrationStatisticsService;
    }

    @GetMapping("hendelser")
    public ResponseEntity<Collection<Event>> getEvents() {
        return ResponseEntity.ok(eventRepository.findAll());
    }

    @GetMapping("hendelser/korrelasjonsid/{correlationId}")
    public ResponseEntity<Collection<Event>> getEventsByCorrelationId(@PathVariable String correlationId) {
        return ResponseEntity.ok(eventRepository.findAllByInstanceFlowHeadersCorrelationId(correlationId));
    }

    @GetMapping("hendelser/instansid/{instanceId}")
    public ResponseEntity<Collection<Event>> getEventsByInstanceId(@PathVariable String instanceId) {
        return ResponseEntity.ok(eventRepository.findAllByInstanceFlowHeadersInstanceId(instanceId));
    }

    @GetMapping("hendelser/integrasjonsid/{integrationId}")
    public ResponseEntity<Collection<Event>> getEventsByIntegrationId(@PathVariable String integrationId) {
        return ResponseEntity.ok(eventRepository.findAllByInstanceFlowHeadersSourceApplicationIntegrationId(integrationId));
    }

    @GetMapping("statistikk/integrasjon")
    public ResponseEntity<Collection<IntegrationStatistics>> getIntegrationStatistics() {
        return ResponseEntity.ok(integrationStatisticsService.getIntegrationStatistics());
    }

}
