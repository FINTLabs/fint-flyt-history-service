package no.fintlabs;

import no.fintlabs.model.*;
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

    private final EventService eventService;
    private final StatisticsService statisticsService;
    @Value("${fint.flyt.resource-server.user-permissions-consumer.enabled:false}")
    private boolean userPermissionsConsumerEnabled;

    public HistoryController(
            EventService eventService,
            StatisticsService statisticsService
    ) {
        this.eventService = eventService;
        this.statisticsService = statisticsService;
    }

//    @GetMapping("hendelser")
//    public ResponseEntity<Page<EventDto>> getEvents(
//            @AuthenticationPrincipal Authentication authentication,
//            @RequestParam(name = "side") int page,
//            @RequestParam(name = "antall") int size,
//            @RequestParam(name = "sorteringFelt") String sortProperty,
//            @RequestParam(name = "sorteringRetning") Sort.Direction sortDirection,
//            @RequestParam(name = "bareSistePerInstans") Optional<Boolean> onlyLatestPerInstance
//    ) {
//        PageRequest pageRequest = PageRequest
//                .of(page, size)
//                .withSort(sortDirection, sortProperty);
//
//        return getResponseEntityEvents(authentication, pageRequest, onlyLatestPerInstance);
//    }

    @GetMapping(value = "hendelser")
    public ResponseEntity<Page<EventDto>> getEvents(
            @AuthenticationPrincipal Authentication authentication,
            @RequestParam(name = "side") int page,
            @RequestParam(name = "antall") int size,
            @RequestParam(name = "sorteringFelt") String sortProperty,
            @RequestParam(name = "sorteringRetning") Sort.Direction sortDirection,
            @RequestParam(name = "bareSistePerInstans") Optional<Boolean> onlyLatestPerInstance,
            @RequestBody(required = false) InstanceSearchParameters instanceSearchParameters
    ) {
        PageRequest pageRequest = PageRequest
                .of(page, size)
                .withSort(sortDirection, sortProperty);

        Optional<InstanceSearchParameters> optionalInstanceSearchParameters = Optional.ofNullable(instanceSearchParameters);

        return getResponseEntityEvents(authentication, pageRequest, onlyLatestPerInstance, optionalInstanceSearchParameters);
    }

    private ResponseEntity<Page<EventDto>> getResponseEntityEvents(
            Authentication authentication,
            Pageable pageable,
            Optional<Boolean> onlyLatestPerInstance,
            Optional<InstanceSearchParameters> optionalInstanceSearchParameters
    ) {

        if (userPermissionsConsumerEnabled) {
            List<Long> sourceApplicationIds =
                    UserAuthorizationUtil.convertSourceApplicationIdsStringToList(authentication);

            return ResponseEntity.ok(
                    onlyLatestPerInstance.orElse(false)
                            ? eventService.getMergedLatestEventsWhereSourceApplicationIdIn(
                            sourceApplicationIds,
                            optionalInstanceSearchParameters,
                            pageable
                    )
                            : eventService.findAllByInstanceFlowHeadersSourceApplicationIdIn(
                            sourceApplicationIds,
                            optionalInstanceSearchParameters,
                            pageable
                    )
            );
        }
        return ResponseEntity.ok(
                onlyLatestPerInstance.orElse(false)
                        ? eventService.getMergedLatestEvents(optionalInstanceSearchParameters, pageable)
                        : eventService.findAll(pageable)
        );
    }

    @GetMapping(path = "hendelser", params = {"kildeapplikasjonId", "kildeapplikasjonInstansId"})
    public ResponseEntity<Page<EventDto>> getEventsWithInstanceId(
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
                eventService
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
        return this.storeManualEvent(
                manuallyProcessedEventDto,
                existingEvent -> this.createManualEvent(
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
        return this.storeManualEvent(
                manuallyRejectedEventDto,
                existingEvent -> this.createManualEvent(
                        existingEvent,
                        "instance-manually-rejected",
                        null
                )
        );
    }

    public ResponseEntity<?> storeManualEvent(ManualEventDto manualEventDto, Function<Event, Event> existingToNewEvent) {
        Optional<Event> optionalEvent = eventService.
                findFirstByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationInstanceIdAndInstanceFlowHeadersSourceApplicationIntegrationIdOrderByTimestampDesc(
                        manualEventDto
                );

        if (optionalEvent.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Event not found.");
        }

        Event event = optionalEvent.get();

        if (!event.getType().equals(EventType.ERROR)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Event is not of type ERROR");
        }

        Event newEvent = existingToNewEvent.apply(event);

        eventService.save(newEvent);

        return ResponseEntity.ok(newEvent);
    }

    public Event createManualEvent(Event event, String name, String archiveId) {
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
            return ResponseEntity.ok(statisticsService.getStatistics(sourceApplicationIds));
        }
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("statistikk/integrasjoner")
    public ResponseEntity<Collection<IntegrationStatistics>> getIntegrationStatistics(
            @AuthenticationPrincipal Authentication authentication
    ) {
        if (userPermissionsConsumerEnabled) {
            List<Long> sourceApplicationIds = UserAuthorizationUtil.convertSourceApplicationIdsStringToList(authentication);
            return ResponseEntity.ok(statisticsService.getIntegrationStatisticsBySourceApplicationId(sourceApplicationIds));
        }

        return ResponseEntity.ok(statisticsService.getIntegrationStatistics());
    }

}
