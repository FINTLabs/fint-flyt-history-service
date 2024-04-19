package no.fintlabs;

import no.fintlabs.model.*;
import no.fintlabs.repositories.EventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
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
    public ResponseEntity<Page<Event>> getEvents(
            @RequestParam(name = "side") int page,
            @RequestParam(name = "antall") int size,
            @RequestParam(name = "sorteringFelt") String sortProperty,
            @RequestParam(name = "sorteringRetning") Sort.Direction sortDirection,
            @RequestParam(name = "bareSistePerInstans") Optional<Boolean> onlyLatestPerInstance
    ) {
        PageRequest pageRequest = PageRequest
                .of(page, size)
                .withSort(sortDirection, sortProperty);

        return ResponseEntity.ok(
                onlyLatestPerInstance.orElse(false)
                        ? eventRepository.findLatestEventPerSourceApplicationInstanceId(pageRequest)
                        : eventRepository.findAll(pageRequest)
        );
    }

    @GetMapping(path = "hendelser", params = {"kildeapplikasjonId", "kildeapplikasjonInstansId"})
    public ResponseEntity<Page<Event>> getEventsWithInstanceId(
            @RequestParam(name = "side") int page,
            @RequestParam(name = "antall") int size,
            @RequestParam(name = "sorteringFelt") String sortProperty,
            @RequestParam(name = "sorteringRetning") Sort.Direction sortDirection,
            @RequestParam(name = "kildeapplikasjonId") Long sourceApplicationId,
            @RequestParam(name = "kildeapplikasjonInstansId") String sourceApplicationInstanceId
    ) {
        PageRequest pageRequest = PageRequest
                .of(page, size)
                .withSort(sortDirection, sortProperty);

        return ResponseEntity.ok(
                eventRepository
                        .findAllByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationInstanceId(
                                sourceApplicationId,
                                sourceApplicationInstanceId,
                                pageRequest
                        )
        );
    }

    @PostMapping("handlinger/instanser/sett-status/manuelt-behandlet-ok")
    public ResponseEntity<Event> setManuallyProcessed(@RequestBody ManualEventDto manualEventDto) {

        Optional<Event> optionalEvent = eventRepository.
                findFirstByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationInstanceIdAndInstanceFlowHeadersSourceApplicationIntegrationIdOrderByTimestampDesc(
                        manualEventDto.getSourceApplicationId(),
                        manualEventDto.getSourceApplicationInstanceId(),
                        manualEventDto.getSourceApplicationIntegrationId()
                );

        optionalEvent.ifPresent(
                event -> {
                    if (event.getType().equals(EventType.ERROR)) {
                        InstanceFlowHeadersEmbeddable newInstanceFlowHeaders = event.getInstanceFlowHeaders()
                                .toBuilder()
                                .archiveInstanceId(manualEventDto.getArchiveInstanceId())
                                .build();

                        Event newEvent = Event
                                .builder()
                                .instanceFlowHeaders(newInstanceFlowHeaders)
                                .name("instance-manually-processed")
                                .timestamp(OffsetDateTime.now())
                                .type(EventType.INFO)
                                .applicationId(event.getApplicationId())
                                .build();

                        newEvent.setInstanceFlowHeaders(newInstanceFlowHeaders);

                        eventRepository.save(newEvent);
                    }
                }
        );

        return ResponseEntity.ok().build();
    }

//    @PostMapping("handlinger/instanser/{instanceId}/sett-status/manuelt-avvist")
//    public ResponseEntity<Event> setManuallyRejected(


    @GetMapping("statistikk")
    public ResponseEntity<Statistics> getStatistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("statistikk/integrasjoner")
    public ResponseEntity<Collection<IntegrationStatistics>> getIntegrationStatistics() {
        return ResponseEntity.ok(statisticsService.getIntegrationStatistics());
    }

}
