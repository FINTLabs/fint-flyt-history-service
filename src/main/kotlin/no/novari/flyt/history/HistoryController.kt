package no.novari.flyt.history

import jakarta.validation.Validator
import jakarta.validation.ValidatorFactory
import no.novari.flyt.history.exceptions.LatestStatusEventNotOfTypeErrorException
import no.novari.flyt.history.exceptions.NoPreviousStatusEventsFoundException
import no.novari.flyt.history.model.action.InstanceStatusTransferredOverrideAction
import no.novari.flyt.history.model.action.ManuallyProcessedEventAction
import no.novari.flyt.history.model.action.ManuallyRejectedEventAction
import no.novari.flyt.history.model.event.Event
import no.novari.flyt.history.model.instance.InstanceFlowSummariesFilter
import no.novari.flyt.history.model.statistics.IntegrationStatisticsFilter
import no.novari.flyt.history.repository.projections.InstanceStatisticsProjection
import no.novari.flyt.history.repository.projections.IntegrationStatisticsProjection
import no.novari.flyt.history.validation.ValidationErrorsFormattingService
import no.novari.flyt.webresourceserver.UrlPaths.INTERNAL_API
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.core.Authentication
import org.springframework.validation.BindException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("$INTERNAL_API/instance-flow-tracking")
class HistoryController(
    private val authorizationService: AuthorizationService,
    private val eventService: EventService,
    private val manualEventCreationService: ManualEventCreationService,
    validatorFactory: ValidatorFactory,
    private val validationErrorsFormattingService: ValidationErrorsFormattingService,
) {
    private val validator: Validator = validatorFactory.validator

    @ExceptionHandler(BindException::class)
    fun handleBindException(exception: BindException): ResponseEntity<String> {
        return ResponseEntity.unprocessableEntity().body(validationErrorsFormattingService.format(exception))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(): ResponseEntity<String> {
        return ResponseEntity.unprocessableEntity().body("Validation error: malformed request body")
    }

    @GetMapping("statistics/total")
    fun getOverallStatistics(authentication: Authentication): ResponseEntity<InstanceStatisticsProjection> {
        val userAuthorizedSourceApplicationIds =
            authorizationService.getUserAuthorizedSourceApplicationIds(authentication)

        return ResponseEntity.ok(eventService.getStatistics(userAuthorizedSourceApplicationIds))
    }

    @GetMapping("statistics/integrations")
    fun getIntegrationStatistics(
        authentication: Authentication,
        integrationStatisticsFilter: IntegrationStatisticsFilter,
        pageable: Pageable,
    ): ResponseEntity<Slice<IntegrationStatisticsProjection>> {
        val intersectionOfAuthorizedAndFilterSourceApplicationIds =
            authorizationService.getIntersectionWithAuthorizedSourceApplicationIds(
                authentication,
                integrationStatisticsFilter.sourceApplicationIds,
            )

        if (intersectionOfAuthorizedAndFilterSourceApplicationIds.isEmpty()) {
            return ResponseEntity.ok(SliceImpl(emptyList(), pageable, false))
        }

        val filterLimitedByUserAuthorization =
            integrationStatisticsFilter.copy(
                sourceApplicationIds = intersectionOfAuthorizedAndFilterSourceApplicationIds,
            )

        return ResponseEntity.ok(eventService.getIntegrationStatistics(filterLimitedByUserAuthorization, pageable))
    }

    @GetMapping("summariesTotalCount")
    fun getInstanceFlowSummariesTotalCount(
        authentication: Authentication,
        instanceFlowSummariesFilter: InstanceFlowSummariesFilter,
    ): ResponseEntity<*> {
        return getInstanceFlowSummariesData(
            authentication = authentication,
            instanceFlowSummariesFilter = instanceFlowSummariesFilter,
            emptyValue = 0,
            eventServiceCallFunction = eventService::getInstanceFlowSummariesTotalCount,
        )
    }

    @GetMapping("summaries")
    fun getInstanceFlowSummaries(
        authentication: Authentication,
        instanceFlowSummariesFilter: InstanceFlowSummariesFilter,
        @RequestParam size: Int,
    ): ResponseEntity<*> {
        return getInstanceFlowSummariesData(
            authentication = authentication,
            instanceFlowSummariesFilter = instanceFlowSummariesFilter,
            emptyValue = emptyList<Event>(),
            eventServiceCallFunction = { filter -> eventService.getInstanceFlowSummaries(filter, size) },
        )
    }

    private fun <T> getInstanceFlowSummariesData(
        authentication: Authentication,
        instanceFlowSummariesFilter: InstanceFlowSummariesFilter,
        emptyValue: T,
        eventServiceCallFunction: (InstanceFlowSummariesFilter) -> T,
    ): ResponseEntity<*> {
        val constraintViolations = validator.validate(instanceFlowSummariesFilter)
        if (constraintViolations.isNotEmpty()) {
            return ResponseEntity.unprocessableEntity().body(
                validationErrorsFormattingService.format(constraintViolations),
            )
        }

        val intersectionOfAuthorizedAndFilterSourceApplicationIds =
            authorizationService.getIntersectionWithAuthorizedSourceApplicationIds(
                authentication,
                instanceFlowSummariesFilter.sourceApplicationIds,
            )

        if (intersectionOfAuthorizedAndFilterSourceApplicationIds.isEmpty()) {
            return ResponseEntity.ok(emptyValue)
        }

        val filterLimitedByUserAuthorization =
            instanceFlowSummariesFilter.copy(
                sourceApplicationIds = intersectionOfAuthorizedAndFilterSourceApplicationIds,
            )

        return ResponseEntity.ok(eventServiceCallFunction(filterLimitedByUserAuthorization))
    }

    @GetMapping(
        "events",
        params = ["sourceApplicationId", "sourceApplicationIntegrationId", "sourceApplicationInstanceId"],
    )
    fun getEventsWithSourceApplicationAggregateInstanceId(
        authentication: Authentication,
        @RequestParam sourceApplicationId: Long,
        @RequestParam sourceApplicationIntegrationId: String,
        @RequestParam sourceApplicationInstanceId: String,
        pageable: Pageable,
    ): ResponseEntity<Page<Event>> {
        authorizationService.validateUserIsAuthorizedForSourceApplication(authentication, sourceApplicationId)
        return ResponseEntity.ok(
            eventService.getAllEventsBySourceApplicationAggregateInstanceId(
                sourceApplicationId,
                sourceApplicationIntegrationId,
                sourceApplicationInstanceId,
                pageable,
            ),
        )
    }

    @PostMapping("events/instance-manually-processed")
    fun setManuallyProcessed(
        authentication: Authentication,
        @RequestBody manuallyProcessedEventAction: ManuallyProcessedEventAction,
    ): ResponseEntity<*> {
        val constraintViolations = validator.validate(manuallyProcessedEventAction)
        if (constraintViolations.isNotEmpty()) {
            return ResponseEntity.unprocessableEntity().body(
                validationErrorsFormattingService.format(constraintViolations),
            )
        }

        authorizationService.validateUserIsAuthorizedForSourceApplication(
            authentication,
            manuallyProcessedEventAction.sourceApplicationId,
        )

        return try {
            ResponseEntity.ok(manualEventCreationService.addManuallyProcessedEvent(manuallyProcessedEventAction))
        } catch (_: NoPreviousStatusEventsFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body("No previous event found")
        } catch (_: LatestStatusEventNotOfTypeErrorException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Previous event status is not of type ERROR")
        }
    }

    @PostMapping("events/instance-manually-rejected")
    fun setManuallyRejected(
        authentication: Authentication,
        @RequestBody manuallyRejectedEventAction: ManuallyRejectedEventAction,
    ): ResponseEntity<*> {
        val constraintViolations = validator.validate(manuallyRejectedEventAction)
        if (constraintViolations.isNotEmpty()) {
            return ResponseEntity.unprocessableEntity().body(
                validationErrorsFormattingService.format(constraintViolations),
            )
        }

        authorizationService.validateUserIsAuthorizedForSourceApplication(
            authentication,
            manuallyRejectedEventAction.sourceApplicationId,
        )

        return try {
            ResponseEntity.ok(manualEventCreationService.addManuallyRejectedEvent(manuallyRejectedEventAction))
        } catch (_: NoPreviousStatusEventsFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body("No previous event found")
        } catch (_: LatestStatusEventNotOfTypeErrorException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Previous event status is not of type ERROR")
        }
    }

    @PostMapping("events/instance-status-overridden-as-transferred")
    fun setInstanceStatusTransferredOverride(
        authentication: Authentication,
        @RequestBody instanceStatusTransferredOverrideAction: InstanceStatusTransferredOverrideAction,
    ): ResponseEntity<*> {
        val constraintViolations = validator.validate(instanceStatusTransferredOverrideAction)
        if (constraintViolations.isNotEmpty()) {
            return ResponseEntity.unprocessableEntity().body(
                validationErrorsFormattingService.format(constraintViolations),
            )
        }

        authorizationService.validateUserIsAuthorizedForSourceApplication(
            authentication,
            instanceStatusTransferredOverrideAction.sourceApplicationId,
        )

        return try {
            ResponseEntity.ok(
                manualEventCreationService.addInstanceStatusOverriddenAsTransferredEvent(
                    instanceStatusTransferredOverrideAction,
                ),
            )
        } catch (_: NoPreviousStatusEventsFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body("No previous event found")
        } catch (_: LatestStatusEventNotOfTypeErrorException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Previous event status is not of type ERROR")
        }
    }
}
