package no.fintlabs;

import no.fintlabs.exceptions.LatestStatusEventNotOfTypeErrorException;
import no.fintlabs.exceptions.NoPreviousStatusEventsFoundException;
import no.fintlabs.model.action.InstanceStatusTransferredOverrideAction;
import no.fintlabs.model.action.ManuallyProcessedEventAction;
import no.fintlabs.model.action.ManuallyRejectedEventAction;
import no.fintlabs.model.event.Event;
import no.fintlabs.model.instance.InstanceFlowSummariesFilter;
import no.fintlabs.model.statistics.IntegrationStatisticsFilter;
import no.fintlabs.repository.projections.InstanceStatisticsProjection;
import no.fintlabs.repository.projections.IntegrationStatisticsProjection;
import no.fintlabs.validation.ValidationErrorsFormattingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.WebExchangeBindException;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static no.fintlabs.resourceserver.UrlPaths.INTERNAL_API;

// TODO 09/04/2025 eivindmorch: Test
@RestController
@RequestMapping(INTERNAL_API + "/instance-flow-tracking")
public class HistoryController {

    private final AuthorizationService authorizationService;
    private final EventService eventService;
    private final ManualEventCreationService manualEventCreationService;
    private final Validator validator;
    private final ValidationErrorsFormattingService validationErrorsFormattingService;

    public HistoryController(
            AuthorizationService authorizationService,
            EventService eventService,
            ManualEventCreationService manualEventCreationService,
            ValidatorFactory validatorFactory,
            ValidationErrorsFormattingService validationErrorsFormattingService) {
        this.authorizationService = authorizationService;
        this.eventService = eventService;
        this.manualEventCreationService = manualEventCreationService;
        this.validator = validatorFactory.getValidator();
        this.validationErrorsFormattingService = validationErrorsFormattingService;
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public final ResponseEntity<?> handleTypeMismatchException(WebExchangeBindException e) {
        return ResponseEntity.unprocessableEntity().body(validationErrorsFormattingService.format(e));
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
        Set<Long> intersectionOfAuthorizedAndFilterSourceApplicationIds =
                authorizationService.getIntersectionWithAuthorizedSourceApplicationIds(
                        authentication,
                        integrationStatisticsFilter.getSourceApplicationIds()
                );

        if (intersectionOfAuthorizedAndFilterSourceApplicationIds.isEmpty()) {
            return ResponseEntity.ok(new SliceImpl<>(List.of(), pageable, false));
        }

        IntegrationStatisticsFilter filterLimitedByUserAuthorization = integrationStatisticsFilter
                .toBuilder()
                .sourceApplicationIds(intersectionOfAuthorizedAndFilterSourceApplicationIds)
                .build();

        return ResponseEntity.ok(
                eventService.getIntegrationStatistics(
                        filterLimitedByUserAuthorization,
                        pageable
                )
        );
    }

    @GetMapping(path = "summariesTotalCount")
    public ResponseEntity<?> getInstanceFlowSummariesTotalCount(
            @AuthenticationPrincipal Authentication authentication,
            InstanceFlowSummariesFilter instanceFlowSummariesFilter
    ) {
        return getInstanceFlowSummariesData(
                authentication,
                instanceFlowSummariesFilter,
                0,
                eventService::getInstanceFlowSummariesTotalCount
        );
    }

    @GetMapping(path = "summaries")
    public ResponseEntity<?> getInstanceFlowSummaries(
            @AuthenticationPrincipal Authentication authentication,
            InstanceFlowSummariesFilter instanceFlowSummariesFilter,
            @RequestParam int size
    ) {
        return getInstanceFlowSummariesData(
                authentication,
                instanceFlowSummariesFilter,
                List.of(),
                filter -> eventService.getInstanceFlowSummaries(filter, size)
        );
    }

    private <T> ResponseEntity<?> getInstanceFlowSummariesData(
            Authentication authentication,
            InstanceFlowSummariesFilter instanceFlowSummariesFilter,
            T emptyValue,
            Function<InstanceFlowSummariesFilter, T> eventServiceCallFunction
    ) {
        Set<ConstraintViolation<InstanceFlowSummariesFilter>> constraintViolations =
                validator.validate(instanceFlowSummariesFilter);

        if (!constraintViolations.isEmpty()) {
            return ResponseEntity.unprocessableEntity()
                    .body(validationErrorsFormattingService.format(constraintViolations));
        }

        Set<Long> intersectionOfAuthorizedAndFilterSourceApplicationIds =
                authorizationService.getIntersectionWithAuthorizedSourceApplicationIds(
                        authentication,
                        instanceFlowSummariesFilter.getSourceApplicationIds()
                );

        if (intersectionOfAuthorizedAndFilterSourceApplicationIds.isEmpty()) {
            return ResponseEntity.ok(emptyValue);
        }

        InstanceFlowSummariesFilter filterLimitedByUserAuthorization = instanceFlowSummariesFilter
                .toBuilder()
                .sourceApplicationIds(intersectionOfAuthorizedAndFilterSourceApplicationIds)
                .build();

        return ResponseEntity.ok(eventServiceCallFunction.apply(filterLimitedByUserAuthorization));
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
            @RequestBody ManuallyProcessedEventAction manuallyProcessedEventAction
    ) {
        authorizationService.validateUserIsAuthorizedForSourceApplication(
                authentication,
                manuallyProcessedEventAction.getSourceApplicationId()
        );

        Set<ConstraintViolation<ManuallyProcessedEventAction>> constraintViolations =
                validator.validate(manuallyProcessedEventAction);
        if (!constraintViolations.isEmpty()) {
            return ResponseEntity.unprocessableEntity()
                    .body(validationErrorsFormattingService.format(constraintViolations));
        }

        try {
            return ResponseEntity.ok(manualEventCreationService.addManuallyProcessedEvent(manuallyProcessedEventAction));
        } catch (NoPreviousStatusEventsFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No previous event found");
        } catch (LatestStatusEventNotOfTypeErrorException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Previous event status is not of type ERROR");
        }
    }

    @PostMapping("events/instance-manually-rejected")
    public ResponseEntity<?> setManuallyRejected(
            @AuthenticationPrincipal Authentication authentication,
            @RequestBody ManuallyRejectedEventAction manuallyRejectedEventAction
    ) {
        authorizationService.validateUserIsAuthorizedForSourceApplication(
                authentication,
                manuallyRejectedEventAction.getSourceApplicationId()
        );

        Set<ConstraintViolation<ManuallyRejectedEventAction>> constraintViolations =
                validator.validate(manuallyRejectedEventAction);
        if (!constraintViolations.isEmpty()) {
            return ResponseEntity.unprocessableEntity()
                    .body(validationErrorsFormattingService.format(constraintViolations));
        }

        try {
            return ResponseEntity.ok(manualEventCreationService.addManuallyRejectedEvent(manuallyRejectedEventAction));
        } catch (NoPreviousStatusEventsFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No previous event found");
        } catch (LatestStatusEventNotOfTypeErrorException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Previous event status is not of type ERROR");
        }
    }

    @PostMapping("events/instance-status-overridden-as-transferred")
    public ResponseEntity<?> setInstanceStatusTransferredOverride(
            @AuthenticationPrincipal Authentication authentication,
            @RequestBody InstanceStatusTransferredOverrideAction instanceStatusTransferredOverrideAction
    ) {
        authorizationService.validateUserIsAuthorizedForSourceApplication(
                authentication,
                instanceStatusTransferredOverrideAction.getSourceApplicationId()
        );

        Set<ConstraintViolation<InstanceStatusTransferredOverrideAction>> constraintViolations =
                validator.validate(instanceStatusTransferredOverrideAction);
        if (!constraintViolations.isEmpty()) {
            return ResponseEntity.unprocessableEntity()
                    .body(validationErrorsFormattingService.format(constraintViolations));
        }

        try {
            return ResponseEntity.ok(manualEventCreationService.addInstanceStatusOverriddenAsTransferredEvent(
                    instanceStatusTransferredOverrideAction
            ));
        } catch (NoPreviousStatusEventsFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No previous event found");
        } catch (LatestStatusEventNotOfTypeErrorException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Previous event status is not of type ERROR");
        }
    }

}
