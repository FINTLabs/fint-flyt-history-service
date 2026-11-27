package no.novari.flyt.history

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Validator
import jakarta.validation.ValidatorFactory
import no.novari.flyt.history.exceptions.RequestValidationException
import no.novari.flyt.history.model.action.InstanceStatusTransferredOverrideAction
import no.novari.flyt.history.model.action.ManuallyProcessedEventAction
import no.novari.flyt.history.model.action.ManuallyRejectedEventAction
import no.novari.flyt.history.model.event.Event
import no.novari.flyt.history.model.instance.InstanceFlowSummariesFilter
import no.novari.flyt.history.model.instance.InstanceFlowSummary
import no.novari.flyt.history.model.response.EventPageResponse
import no.novari.flyt.history.model.response.IntegrationStatisticsResponse
import no.novari.flyt.history.model.statistics.IntegrationStatisticsFilter
import no.novari.flyt.history.repository.projections.InstanceStatisticsProjection
import no.novari.flyt.history.validation.ValidationErrorsFormattingService
import no.novari.flyt.security.UrlPaths.INTERNAL_API
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("$INTERNAL_API/instance-flow-tracking")
@Tag(name = "Instance-flow history", description = "History, statistics, and remediation for Flyt instances.")
class HistoryController(
    private val authorizationService: AuthorizationService,
    private val eventService: EventService,
    private val manualEventCreationService: ManualEventCreationService,
    validatorFactory: ValidatorFactory,
    private val validationErrorsFormattingService: ValidationErrorsFormattingService,
) {
    private val validator: Validator = validatorFactory.validator

    @GetMapping("statistics/total")
    @Operation(summary = "Get overall instance statistics")
    fun getOverallStatistics(): InstanceStatisticsProjection {
        val userAuthorizedSourceApplicationIds =
            authorizationService.getUserAuthorizedSourceApplicationIds(
                eventService.findDistinctSourceApplicationIds(),
            )

        return eventService.getStatistics(userAuthorizedSourceApplicationIds)
    }

    @GetMapping("statistics/integrations")
    @Operation(summary = "Get instance statistics grouped by integration")
    fun getIntegrationStatistics(
        @ParameterObject
        integrationStatisticsFilter: IntegrationStatisticsFilter,
        @ParameterObject
        pageable: Pageable,
    ): IntegrationStatisticsResponse {
        val intersectionOfAuthorizedAndFilterSourceApplicationIds =
            getAuthorizedSourceApplicationIds(
                integrationStatisticsFilter.sourceApplicationIds,
            )

        if (intersectionOfAuthorizedAndFilterSourceApplicationIds.isEmpty()) {
            return IntegrationStatisticsResponse(content = emptyList())
        }

        val filterLimitedByUserAuthorization =
            integrationStatisticsFilter.copy(
                sourceApplicationIds = intersectionOfAuthorizedAndFilterSourceApplicationIds,
            )

        val statistics = eventService.getIntegrationStatistics(filterLimitedByUserAuthorization, pageable)

        return IntegrationStatisticsResponse(content = statistics.content)
    }

    @GetMapping("summariesTotalCount")
    @Operation(summary = "Count instance-flow summaries")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Matching summary count"),
            ApiResponse(responseCode = "422", description = "Invalid filter"),
        ],
    )
    fun getInstanceFlowSummariesTotalCount(
        @ParameterObject
        instanceFlowSummariesFilter: InstanceFlowSummariesFilter,
    ): Long =
        getInstanceFlowSummariesData(
            instanceFlowSummariesFilter = instanceFlowSummariesFilter,
            emptyValue = 0L,
            eventServiceCallFunction = eventService::getInstanceFlowSummariesTotalCount,
        )

    @GetMapping("summaries")
    @Operation(summary = "List instance-flow summaries")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Matching summaries"),
            ApiResponse(responseCode = "422", description = "Invalid filter"),
        ],
    )
    fun getInstanceFlowSummaries(
        @ParameterObject
        instanceFlowSummariesFilter: InstanceFlowSummariesFilter,
        @Parameter(description = "Maximum number of summaries to return")
        @RequestParam size: Int,
    ): List<InstanceFlowSummary> =
        getInstanceFlowSummariesData(
            instanceFlowSummariesFilter = instanceFlowSummariesFilter,
            emptyValue = emptyList(),
            eventServiceCallFunction = { filter -> eventService.getInstanceFlowSummaries(filter, size) },
        )

    private fun <T> getInstanceFlowSummariesData(
        instanceFlowSummariesFilter: InstanceFlowSummariesFilter,
        emptyValue: T,
        eventServiceCallFunction: (InstanceFlowSummariesFilter) -> T,
    ): T {
        validate(instanceFlowSummariesFilter)

        val intersectionOfAuthorizedAndFilterSourceApplicationIds =
            getAuthorizedSourceApplicationIds(
                instanceFlowSummariesFilter.sourceApplicationIds,
            )

        if (intersectionOfAuthorizedAndFilterSourceApplicationIds.isEmpty()) {
            return emptyValue
        }

        val filterLimitedByUserAuthorization =
            instanceFlowSummariesFilter.copy(
                sourceApplicationIds = intersectionOfAuthorizedAndFilterSourceApplicationIds,
            )

        return eventServiceCallFunction(filterLimitedByUserAuthorization)
    }

    private fun getAuthorizedSourceApplicationIds(requestedSourceApplicationIds: Collection<Long>?): Set<Long> {
        return authorizationService.getUserAuthorizedSourceApplicationIds(
            requestedSourceApplicationIds?.toSet() ?: eventService.findDistinctSourceApplicationIds(),
        )
    }

    @GetMapping(
        "events",
        params = ["sourceApplicationId", "sourceApplicationIntegrationId", "sourceApplicationInstanceId"],
    )
    @Operation(summary = "Get the event timeline for an instance")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Event timeline"),
            ApiResponse(responseCode = "403", description = "Source application access denied"),
        ],
    )
    fun getEventsWithSourceApplicationAggregateInstanceId(
        @Parameter(description = "Source application identifier")
        @RequestParam sourceApplicationId: Long,
        @Parameter(description = "Source application integration identifier")
        @RequestParam sourceApplicationIntegrationId: String,
        @Parameter(description = "Source application instance identifier")
        @RequestParam sourceApplicationInstanceId: String,
        @ParameterObject
        pageable: Pageable,
    ): EventPageResponse {
        authorizationService.validateUserIsAuthorizedForSourceApplication(sourceApplicationId)
        val events =
            eventService.getAllEventsBySourceApplicationAggregateInstanceId(
                sourceApplicationId,
                sourceApplicationIntegrationId,
                sourceApplicationInstanceId,
                pageable,
            )
        return EventPageResponse(
            content = events.content,
            last = events.isLast,
        )
    }

    @PostMapping("events/instance-manually-processed")
    @Operation(summary = "Mark an instance as manually processed")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Manual event created"),
            ApiResponse(responseCode = "400", description = "Instance is not in an error state"),
            ApiResponse(responseCode = "404", description = "Previous event not found"),
            ApiResponse(responseCode = "422", description = "Invalid request"),
        ],
    )
    fun setManuallyProcessed(
        @RequestBody manuallyProcessedEventAction: ManuallyProcessedEventAction,
    ): Event {
        validate(manuallyProcessedEventAction)

        authorizationService.validateUserIsAuthorizedForSourceApplication(
            manuallyProcessedEventAction.sourceApplicationId,
        )

        return manualEventCreationService.addManuallyProcessedEvent(manuallyProcessedEventAction)
    }

    @PostMapping("events/instance-manually-rejected")
    @Operation(summary = "Mark an instance as manually rejected")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Manual event created"),
            ApiResponse(responseCode = "400", description = "Instance is not in an error state"),
            ApiResponse(responseCode = "404", description = "Previous event not found"),
            ApiResponse(responseCode = "422", description = "Invalid request"),
        ],
    )
    fun setManuallyRejected(
        @RequestBody manuallyRejectedEventAction: ManuallyRejectedEventAction,
    ): Event {
        validate(manuallyRejectedEventAction)

        authorizationService.validateUserIsAuthorizedForSourceApplication(
            manuallyRejectedEventAction.sourceApplicationId,
        )

        return manualEventCreationService.addManuallyRejectedEvent(manuallyRejectedEventAction)
    }

    @PostMapping("events/instance-status-overridden-as-transferred")
    @Operation(summary = "Override an instance status as transferred")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Status override event created"),
            ApiResponse(responseCode = "400", description = "Instance is not in an error state"),
            ApiResponse(responseCode = "404", description = "Previous event not found"),
            ApiResponse(responseCode = "422", description = "Invalid request"),
        ],
    )
    fun setInstanceStatusTransferredOverride(
        @RequestBody instanceStatusTransferredOverrideAction: InstanceStatusTransferredOverrideAction,
    ): Event {
        validate(instanceStatusTransferredOverrideAction)

        authorizationService.validateUserIsAuthorizedForSourceApplication(
            instanceStatusTransferredOverrideAction.sourceApplicationId,
        )

        return manualEventCreationService.addInstanceStatusOverriddenAsTransferredEvent(
            instanceStatusTransferredOverrideAction,
        )
    }

    private fun validate(target: Any) {
        val constraintViolations = validator.validate(target)
        if (constraintViolations.isNotEmpty()) {
            throw RequestValidationException(validationErrorsFormattingService.format(constraintViolations))
        }
    }
}
