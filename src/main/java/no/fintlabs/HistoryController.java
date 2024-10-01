package no.fintlabs;

import no.fintlabs.model.*;
import no.fintlabs.resourceserver.security.user.UserAuthorizationUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    @GetMapping("hendelser")
    public ResponseEntity<Page<EventDto>> getEvents(
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

    private ResponseEntity<Page<EventDto>> getResponseEntityEvents(
            Authentication authentication,
            Pageable pageable,
            Optional<Boolean> onlyLatestPerInstance
    ) {
        if (userPermissionsConsumerEnabled) {
            List<Long> sourceApplicationIds =
                    UserAuthorizationUtil.convertSourceApplicationIdsStringToList(authentication);

            return ResponseEntity.ok(
                    onlyLatestPerInstance.orElse(false)
                            ? eventService
                            .getMergedLatestEventsWhereSourceApplicationIdIn(
                                    sourceApplicationIds,
                                    pageable
                            )
                            : eventService.findAllByInstanceFlowHeadersSourceApplicationIdIn(sourceApplicationIds, pageable));
        }
        return ResponseEntity.ok(
                onlyLatestPerInstance.orElse(false)
                        ? eventService.getMergedLatestEvents(pageable)
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
        return eventService.storeManualEvent(
                manuallyProcessedEventDto,
                existingEvent -> eventService.createManualEvent(
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
        return eventService.storeManualEvent(
                manuallyRejectedEventDto,
                existingEvent -> eventService.createManualEvent(
                        existingEvent,
                        "instance-manually-rejected",
                        null
                )
        );
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
