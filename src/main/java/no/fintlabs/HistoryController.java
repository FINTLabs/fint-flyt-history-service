package no.fintlabs;

import no.fintlabs.model.Event;
import no.fintlabs.model.IntegrationStatistics;
import no.fintlabs.model.Statistics;
import no.fintlabs.repositories.EventRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Optional;

import static no.fintlabs.resourceserver.UrlPaths.INTERNAL_API;

@RestController
@RequestMapping(INTERNAL_API + "/historikk")
public class HistoryController {

    private final EventRepository eventRepository;
    private final StatisticsService statisticsService;

    public HistoryController(EventRepository eventRepository, StatisticsService statisticsService) {
        this.eventRepository = eventRepository;
        this.statisticsService = statisticsService;
    }

    @GetMapping("hendelser")
    public ResponseEntity<Collection<Event>> getEvents(
            @RequestParam(name = "bareSistePerIntegrasjon") Optional<Boolean> onlyLatestPerIntegration
    ) {
        return ResponseEntity.ok(
                onlyLatestPerIntegration.orElse(false)
                        ? eventRepository.findLatestEventPerSourceApplicationInstanceId()
                        : eventRepository.findAll()
        );
    }

    @GetMapping(path = "hendelser", params = {"kildeapplikasjonId", "kildeapplikasjonInstansId"})
    public ResponseEntity<Collection<Event>> getEventsWithInstanceId(
            @RequestParam(name = "kildeapplikasjonId") Long sourceApplicationId,
            @RequestParam(name = "kildeapplikasjonInstansId") String sourceApplicationInstanceId
    ) {
        return ResponseEntity.ok(eventRepository
                .findAllByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationInstanceId(
                        sourceApplicationId,
                        sourceApplicationInstanceId
                )
        );
    }

    @GetMapping("statistikk")
    public ResponseEntity<Statistics> getStatistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("statistikk/integrasjoner")
    public ResponseEntity<Collection<IntegrationStatistics>> getIntegrationStatistics() {
        return ResponseEntity.ok(statisticsService.getIntegrationStatistics());
    }

}
