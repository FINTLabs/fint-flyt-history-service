package no.fintlabs;

import no.fintlabs.model.*;
import no.fintlabs.repositories.EventRepository;
import no.fintlabs.resourceserver.security.user.UserAuthorizationUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static no.fintlabs.resourceserver.UrlPaths.INTERNAL_API;

@RestController
@RequestMapping(INTERNAL_API + "/historikk")
public class HistoryController {

    private final EventRepository eventRepository;
    private final StatisticsService statisticsService;
    @Value("${fint.flyt.resource-server.user-permissions-consumer.enabled:false}")
    private boolean userPermissionsConsumerEnabled;

    public HistoryController(EventRepository eventRepository, StatisticsService statisticsService) {
        this.eventRepository = eventRepository;
        this.statisticsService = statisticsService;
    }

    @GetMapping("hendelser")
    public ResponseEntity<Page<Event>> getEvents(
            @AuthenticationPrincipal Authentication authentication,
            @RequestParam(name = "side") int page,
            @RequestParam(name = "antall") int size,
            @RequestParam(name = "sorteringFelt") String sortProperty,
            @RequestParam(name = "sorteringRetning") Sort.Direction sortDirection,
            @RequestParam(name = "bareSistePerInstans") Optional<Boolean> onlyLatestPerInstance
    ) {
        PageRequest pageRequest = PageRequest
                .of(page, size)
                .withSort(sortDirection, sortProperty);

        return getResponseEntityEvents(authentication, pageRequest, onlyLatestPerInstance);
    }

    private ResponseEntity<Page<Event>> getResponseEntityEvents(
            Authentication authentication,
            Pageable pageable,
            Optional<Boolean> onlyLatestPerInstance
    ) {
        if (userPermissionsConsumerEnabled) {
            List<Long> sourceApplicationIds =
                    UserAuthorizationUtil.convertSourceApplicationIdsStringToList(authentication);

            return ResponseEntity.ok(
                    onlyLatestPerInstance.orElse(false)
                            ? eventRepository
                            .findLatestEventPerSourceApplicationInstanceIdAndSourceApplicationIdIn(
                                    sourceApplicationIds,
                                    pageable
                            )
                            : eventRepository.findAllByInstanceFlowHeadersSourceApplicationIdIn(sourceApplicationIds, pageable));
        }
        return ResponseEntity.ok(
                onlyLatestPerInstance.orElse(false)
                        ? eventRepository.findLatestEventPerSourceApplicationInstanceId(pageable)
                        : eventRepository.findAll(pageable)
        );
    }

    @GetMapping(path = "hendelser", params = {"kildeapplikasjonId", "kildeapplikasjonInstansId"})
    public ResponseEntity<Page<Event>> getEventsWithInstanceId(
            @AuthenticationPrincipal Authentication authentication,
            @RequestParam(name = "side") int page,
            @RequestParam(name = "antall") int size,
            @RequestParam(name = "sorteringFelt") String sortProperty,
            @RequestParam(name = "sorteringRetning") Sort.Direction sortDirection,
            @RequestParam(name = "kildeapplikasjonId") Long sourceApplicationId,
            @RequestParam(name = "kildeapplikasjonInstansId") String sourceApplicationInstanceId
    ) {
        if (userPermissionsConsumerEnabled) {
            UserAuthorizationUtil.checkIfUserHasAccessToSourceApplication(authentication, sourceApplicationId);
        }
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
    public ResponseEntity<?> setManuallyProcessed(
            @RequestBody @Valid ManuallyProcessedEventDto manuallyProcessedEventDto,
            @AuthenticationPrincipal Authentication authentication
    ) {
        if (userPermissionsConsumerEnabled) {
            UserAuthorizationUtil.checkIfUserHasAccessToSourceApplication(authentication, manuallyProcessedEventDto.getSourceApplicationId());
        }
        return storeManualEvent(
                manuallyProcessedEventDto,
                existingEvent -> createManualEvent(
                        existingEvent,
                        "instance-manually-processed",
                        manuallyProcessedEventDto.getArchiveInstanceId()
                )
        );
    }

    @PostMapping("handlinger/instanser/sett-status/manuelt-avvist")
    public ResponseEntity<?> setManuallyRejected(
            @RequestBody @Valid ManuallyRejectedEventDto manuallyRejectedEventDto,
            @AuthenticationPrincipal Authentication authentication
    ) {
        if (userPermissionsConsumerEnabled) {
            UserAuthorizationUtil.checkIfUserHasAccessToSourceApplication(authentication, manuallyRejectedEventDto.getSourceApplicationId());
        }
        return storeManualEvent(
                manuallyRejectedEventDto,
                existingEvent -> createManualEvent(
                        existingEvent,
                        "instance-manually-rejected",
                        null
                )
        );
    }

    private ResponseEntity<?> storeManualEvent(ManualEventDto manualEventDto, Function<Event, Event> existingToNewEvent) {
        Optional<Event> optionalEvent = eventRepository.
                findFirstByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationInstanceIdAndInstanceFlowHeadersSourceApplicationIntegrationIdOrderByTimestampDesc(
                        manualEventDto.getSourceApplicationId(),
                        manualEventDto.getSourceApplicationInstanceId(),
                        manualEventDto.getSourceApplicationIntegrationId()
                );

        if (optionalEvent.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Event not found.");
        }

        Event event = optionalEvent.get();

        if (!event.getType().equals(EventType.ERROR)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Event is not of type ERROR");
        }

        Event newEvent = existingToNewEvent.apply(event);

        eventRepository.save(newEvent);

        return ResponseEntity.ok(newEvent);
    }

    private Event createManualEvent(Event event, String name, String archiveId) {

        InstanceFlowHeadersEmbeddable.InstanceFlowHeadersEmbeddableBuilder headersEmbeddableBuilder =
                event.getInstanceFlowHeaders()
                        .toBuilder()
                        .correlationId(UUID.randomUUID());

        if (archiveId != null && !archiveId.isEmpty()) {
            headersEmbeddableBuilder.archiveInstanceId(archiveId);
        }

        InstanceFlowHeadersEmbeddable newInstanceFlowHeaders = headersEmbeddableBuilder.build();

        return Event.builder()
                .instanceFlowHeaders(newInstanceFlowHeaders)
                .name(name)
                .timestamp(OffsetDateTime.now())
                .type(EventType.INFO)
                .applicationId(event.getApplicationId())
                .build();
    }

    @GetMapping("statistikk")
    public ResponseEntity<Statistics> getStatistics(
            @AuthenticationPrincipal Authentication authentication
    ) {
        if (userPermissionsConsumerEnabled) {
            List<Long> sourceApplicationIds = UserAuthorizationUtil.convertSourceApplicationIdsStringToList(authentication);
            return ResponseEntity.ok(statisticsService.getStatisticsBySourceApplicationIds(sourceApplicationIds));
        }
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("statistikk/integrasjoner")
    public ResponseEntity<Collection<IntegrationStatistics>> getIntegrationStatistics() {
        return ResponseEntity.ok(statisticsService.getIntegrationStatistics());
    }

}
