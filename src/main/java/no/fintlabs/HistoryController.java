package no.fintlabs;

import no.fintlabs.exceptions.LatesStatusEventNotOfTypeErrorException;
import no.fintlabs.exceptions.NoPreviousStatusEventsFoundException;
import no.fintlabs.model.action.ManuallyProcessedEventAction;
import no.fintlabs.model.action.ManuallyRejectedEventAction;
import no.fintlabs.model.event.Event;
import no.fintlabs.model.instance.InstanceFlowSummariesFilter;
import no.fintlabs.model.instance.InstanceFlowSummary;
import no.fintlabs.model.statistics.IntegrationStatisticsFilter;
import no.fintlabs.repository.projections.InstanceStatisticsProjection;
import no.fintlabs.repository.projections.IntegrationStatisticsProjection;
import org.springframework.beans.TypeMismatchException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Set;

import static no.fintlabs.resourceserver.UrlPaths.INTERNAL_API;

@RestController
@RequestMapping(INTERNAL_API + "/instance-flow-tracking")
public class HistoryController {

    private final AuthorizationService authorizationService;
    private final EventService eventService;

    public HistoryController(
            AuthorizationService authorizationService,
            EventService eventService
    ) {
        this.authorizationService = authorizationService;
        this.eventService = eventService;
    }

    // TODO 07/02/2025 eivindmorch: Add format and response to frontend with validation errors

    @ExceptionHandler(TypeMismatchException.class)
    public final ResponseEntity<?> handleTypeMismatchException(TypeMismatchException e) {
        return ResponseEntity.badRequest().body("'" + e.getValue() + "' does not match the required type or format");
    }

    @GetMapping("statistics/total")
    public ResponseEntity<InstanceStatisticsProjection> getOverallStatistics(
            @AuthenticationPrincipal Authentication authentication
    ) {
        Set<Long> userAuthorizedSourceApplicationIds =
                authorizationService.getUserAuthorizedSourceApplicationIds(authentication);
        return ResponseEntity.ok(eventService.getStatistics(userAuthorizedSourceApplicationIds));
    }

    @GetMapping("statistics/integrations")
    public ResponseEntity<Slice<IntegrationStatisticsProjection>> getIntegrationStatistics(
            @AuthenticationPrincipal Authentication authentication,
            IntegrationStatisticsFilter integrationStatisticsFilter,
            Pageable pageable
    ) {
        IntegrationStatisticsFilter filterLimitedByUserAuthorization =
                authorizationService.createNewFilterLimitedByUserAuthorizedSourceApplicationIds(
                        authentication,
                        integrationStatisticsFilter
                );
        return ResponseEntity.ok(
                eventService.getIntegrationStatistics(
                        filterLimitedByUserAuthorization,
                        pageable
                )
        );
    }

    @GetMapping(path = "summaries")
    public ResponseEntity<Slice<InstanceFlowSummary>> getInstanceFlowSummaries(
            @AuthenticationPrincipal Authentication authentication,
            @Valid InstanceFlowSummariesFilter instanceFlowSummariesFilter,
            Pageable pageable
    ) {
        InstanceFlowSummariesFilter filterLimitedByUserAuthorization =
                authorizationService.createNewFilterLimitedByUserAuthorizedSourceApplicationIds(
                        authentication,
                        instanceFlowSummariesFilter
                );
        return ResponseEntity.ok(
                eventService.getInstanceFlowSummaries(
                        filterLimitedByUserAuthorization,
                        pageable
                )
        );
    }

    @GetMapping(path = "events", params = {
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
        authorizationService.validateUserIsAuthorizedForSourceApplication(authentication, sourceApplicationId);
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
    @PostMapping("events/instance-manually-processed")
    public ResponseEntity<?> setManuallyProcessed(
            @AuthenticationPrincipal Authentication authentication,
            @RequestBody @Valid ManuallyProcessedEventAction manuallyProcessedEventAction
    ) {
        authorizationService.validateUserIsAuthorizedForSourceApplication(
                authentication,
                manuallyProcessedEventAction.getSourceApplicationId()
        );
        try {
            return ResponseEntity.ok(eventService.addManuallyProcessedEvent(manuallyProcessedEventAction));
        } catch (NoPreviousStatusEventsFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No previous event not found");
        } catch (LatesStatusEventNotOfTypeErrorException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Previous event status is not of type ERROR");
        }
    }

    @PostMapping("events/instance-manually-rejected")
    public ResponseEntity<?> setManuallyRejected(
            @AuthenticationPrincipal Authentication authentication,
            @RequestBody @Valid ManuallyRejectedEventAction manuallyRejectedEventAction
    ) {
        authorizationService.validateUserIsAuthorizedForSourceApplication(
                authentication,
                manuallyRejectedEventAction.getSourceApplicationId()
        );
        try {
            return ResponseEntity.ok(eventService.addManuallyRejectedEvent(manuallyRejectedEventAction));
        } catch (NoPreviousStatusEventsFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No previous event not found");
        } catch (LatesStatusEventNotOfTypeErrorException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Previous event status is not of type ERROR");
        }
    }

}
