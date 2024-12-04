package no.fintlabs;

import no.fintlabs.exceptions.LatesStatusEventNotOfTypeErrorException;
import no.fintlabs.exceptions.NoPreviousStatusEventsFoundException;
import no.fintlabs.model.*;
import no.fintlabs.resourceserver.security.user.UserAuthorizationUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static no.fintlabs.resourceserver.UrlPaths.INTERNAL_API;

@RestController
@RequestMapping(INTERNAL_API + "/history")
public class HistoryController {

    private final StatisticsService statisticsService;
    private final EventService eventService;

    public HistoryController(
            StatisticsService statisticsService,
            EventService eventService
    ) {
        this.statisticsService = statisticsService;
        this.eventService = eventService;
    }

    @GetMapping("statistics")
    public ResponseEntity<Statistics> getOverallStatistics(
            @AuthenticationPrincipal Authentication authentication
    ) {
        List<Long> sourceApplicationIds = UserAuthorizationUtil.convertSourceApplicationIdsStringToList(authentication);
        return ResponseEntity.ok(statisticsService.getStatistics(sourceApplicationIds));
    }

    @GetMapping("statistics/integrations")
    public ResponseEntity<Page<IntegrationStatistics>> getIntegrationStatistics(
            @AuthenticationPrincipal Authentication authentication,
            @RequestParam Optional<Set<Long>> filterSourceApplicationIds,
            @RequestParam Optional<Set<String>> filterSourceApplicationIntegrationIds,
            @RequestParam Optional<Set<Long>> filterIntegrationIds,
            Pageable pageable
    ) {
        Set<Long> userAuthorizationSourceApplicationIds =
                new HashSet<>(UserAuthorizationUtil.convertSourceApplicationIdsStringToList(authentication));

        return ResponseEntity.ok(
                statisticsService.getIntegrationStatistics(
                        userAuthorizationSourceApplicationIds,
                        filterSourceApplicationIds.orElse(null),
                        filterSourceApplicationIntegrationIds.orElse(null),
                        filterIntegrationIds.orElse(null),
                        pageable
                )
        );
    }

    // TODO 04/12/2024 eivindmorch: Get instance status with filter and sort and page (instance table in frontend)

    @GetMapping(path = "event", params = {
            "sourceApplicationId",
            "sourceApplicationIntegrationId",
            "sourceApplicationInstanceId"
    })
    public ResponseEntity<Page<EventDto>> getEventsWithSourceApplicationAggregateInstanceId(
            @AuthenticationPrincipal Authentication authentication,
            @RequestParam Long sourceApplicationId,
            @RequestParam String sourceApplicationIntegrationId,
            @RequestParam String sourceApplicationInstanceId,
            Pageable pageable
    ) {
        UserAuthorizationUtil.checkIfUserHasAccessToSourceApplication(authentication, sourceApplicationId);
        return ResponseEntity.ok(
                eventService.getAllEventsBySourceApplicationAggregateInstanceId(
                        sourceApplicationId,
                        sourceApplicationIntegrationId,
                        sourceApplicationInstanceId,
                        pageable
                )
        );
    }

    // TODO 04/12/2024 eivindmorch: Ved dispatched/manual burde vi slette alle instanser som har samme SA, SAIntId, SAInstId
    //  Aggregere alle instanceId og fileId som ligger i history med den kobinasjonen av SA, SAIntId, SAInstId
    //  Da trenger vi ikke kopiere headers for manuelle eventer her
    @PostMapping("action/event/instance-manually-processed")
    public ResponseEntity<?> setManuallyProcessed(
            @AuthenticationPrincipal Authentication authentication,
            @RequestBody @Valid ManuallyProcessedEventDto manuallyProcessedEventDto
    ) {
        UserAuthorizationUtil.checkIfUserHasAccessToSourceApplication(authentication, manuallyProcessedEventDto.getSourceApplicationId());
        try {
            return ResponseEntity.ok(eventService.addManuallyProcessedEvent(manuallyProcessedEventDto));
        } catch (NoPreviousStatusEventsFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No previous event not found");
        } catch (LatesStatusEventNotOfTypeErrorException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Previous event status is not of type ERROR");
        }
    }

    @PostMapping("action/event/instance-manually-rejected")
    public ResponseEntity<?> setManuallyRejected(
            @AuthenticationPrincipal Authentication authentication,
            @RequestBody @Valid ManuallyRejectedEventDto manuallyRejectedEventDto
    ) {
        UserAuthorizationUtil.checkIfUserHasAccessToSourceApplication(authentication, manuallyRejectedEventDto.getSourceApplicationId());
        try {
            return ResponseEntity.ok(eventService.addManuallyRejectedEvent(manuallyRejectedEventDto));
        } catch (NoPreviousStatusEventsFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No previous event not found");
        } catch (LatesStatusEventNotOfTypeErrorException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Previous event status is not of type ERROR");
        }
    }

}
