package no.fintlabs;

import no.fintlabs.exceptions.LatesStatusEventNotOfTypeErrorException;
import no.fintlabs.exceptions.NoPreviousStatusEventsFoundException;
import no.fintlabs.model.Event;
import no.fintlabs.model.InstanceStatus;
import no.fintlabs.model.InstanceStatusFilter;
import no.fintlabs.model.action.ManuallyProcessedEventAction;
import no.fintlabs.model.action.ManuallyRejectedEventAction;
import no.fintlabs.model.statistics.IntegrationStatistics;
import no.fintlabs.model.statistics.IntegrationStatisticsFilter;
import no.fintlabs.model.statistics.Statistics;
import no.fintlabs.resourceserver.security.user.UserAuthorizationUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

import static no.fintlabs.resourceserver.UrlPaths.INTERNAL_API;

@RestController
@RequestMapping(INTERNAL_API + "/history")
public class HistoryController {

    private final EventService eventService;

    public HistoryController(
            EventService eventService
    ) {
        this.eventService = eventService;
    }

    @GetMapping("statistics")
    public ResponseEntity<Statistics> getOverallStatistics(
            @AuthenticationPrincipal Authentication authentication
    ) {
        List<Long> sourceApplicationIds = UserAuthorizationUtil.convertSourceApplicationIdsStringToList(authentication);
        return ResponseEntity.ok(eventService.getStatistics(sourceApplicationIds));
    }

    @GetMapping("statistics/integrations")
    public ResponseEntity<Page<IntegrationStatistics>> getIntegrationStatistics(
            @AuthenticationPrincipal Authentication authentication,
            IntegrationStatisticsFilter integrationStatisticsFilter,
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                eventService.getIntegrationStatistics(
                        UserAuthorizationUtil.convertSourceApplicationIdsStringToList(authentication),
                        integrationStatisticsFilter,
                        pageable
                )
        );
    }

    @GetMapping(path = "instance-statuses")
    public ResponseEntity<Page<InstanceStatus>> getInstanceStatus(
            @AuthenticationPrincipal Authentication authentication,
            InstanceStatusFilter instanceStatusFilter,
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                eventService.getInstanceStatuses(
                        UserAuthorizationUtil.convertSourceApplicationIdsStringToList(authentication),
                        instanceStatusFilter,
                        pageable
                )
        );
    }

    @GetMapping(path = "event", params = {
            "sourceApplicationId",
            "sourceApplicationIntegrationId",
            "sourceApplicationInstanceId"
    })
    public ResponseEntity<Page<Event>> getEventsWithSourceApplicationAggregateInstanceId(
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
            @RequestBody @Valid ManuallyProcessedEventAction manuallyProcessedEventAction
    ) {
        UserAuthorizationUtil.checkIfUserHasAccessToSourceApplication(authentication, manuallyProcessedEventAction.getSourceApplicationId());
        try {
            return ResponseEntity.ok(eventService.addManuallyProcessedEvent(manuallyProcessedEventAction));
        } catch (NoPreviousStatusEventsFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No previous event not found");
        } catch (LatesStatusEventNotOfTypeErrorException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Previous event status is not of type ERROR");
        }
    }

    @PostMapping("action/event/instance-manually-rejected")
    public ResponseEntity<?> setManuallyRejected(
            @AuthenticationPrincipal Authentication authentication,
            @RequestBody @Valid ManuallyRejectedEventAction manuallyRejectedEventAction
    ) {
        UserAuthorizationUtil.checkIfUserHasAccessToSourceApplication(authentication, manuallyRejectedEventAction.getSourceApplicationId());
        try {
            return ResponseEntity.ok(eventService.addManuallyRejectedEvent(manuallyRejectedEventAction));
        } catch (NoPreviousStatusEventsFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No previous event not found");
        } catch (LatesStatusEventNotOfTypeErrorException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Previous event status is not of type ERROR");
        }
    }

}
