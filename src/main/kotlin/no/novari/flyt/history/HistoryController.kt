package no.novari.flyt.history

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
import no.novari.flyt.webresourceserver.UrlPaths.INTERNAL_API
import org.springframework.data.domain.Pageable
import org.springframework.security.core.Authentication
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

    @GetMapping("statistics/total")
    fun getOverallStatistics(authentication: Authentication): InstanceStatisticsProjection {
        val userAuthorizedSourceApplicationIds =
            authorizationService.getUserAuthorizedSourceApplicationIds(
                authentication,
                eventService.findDistinctSourceApplicationIds(),
            )

        return eventService.getStatistics(userAuthorizedSourceApplicationIds)
    }

    @GetMapping("statistics/integrations")
    fun getIntegrationStatistics(
        authentication: Authentication,
        integrationStatisticsFilter: IntegrationStatisticsFilter,
        pageable: Pageable,
    ): IntegrationStatisticsResponse {
        val intersectionOfAuthorizedAndFilterSourceApplicationIds =
            getAuthorizedSourceApplicationIds(
                authentication,
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

    @GetMapping("summaries")
    fun getInstanceFlowSummaries(
        authentication: Authentication,
        instanceFlowSummariesFilter: InstanceFlowSummariesFilter,
        @RequestParam size: Int,
    ): List<InstanceFlowSummary> =
        getInstanceFlowSummariesData(
            authentication = authentication,
            instanceFlowSummariesFilter = instanceFlowSummariesFilter,
            emptyValue = emptyList(),
            eventServiceCallFunction = { filter -> eventService.getInstanceFlowSummaries(filter, size) },
        )

    private fun <T> getInstanceFlowSummariesData(
        authentication: Authentication,
        instanceFlowSummariesFilter: InstanceFlowSummariesFilter,
        emptyValue: T,
        eventServiceCallFunction: (InstanceFlowSummariesFilter) -> T,
    ): T {
        validate(instanceFlowSummariesFilter)

        val intersectionOfAuthorizedAndFilterSourceApplicationIds =
            getAuthorizedSourceApplicationIds(
                authentication,
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

    private fun getAuthorizedSourceApplicationIds(
        authentication: Authentication,
        requestedSourceApplicationIds: Collection<Long>?,
    ): Set<Long> {
        return authorizationService.getUserAuthorizedSourceApplicationIds(
            authentication,
            requestedSourceApplicationIds?.toSet() ?: eventService.findDistinctSourceApplicationIds(),
        )
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
    ): EventPageResponse {
        authorizationService.validateUserIsAuthorizedForSourceApplication(authentication, sourceApplicationId)
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
    fun setManuallyProcessed(
        authentication: Authentication,
        @RequestBody manuallyProcessedEventAction: ManuallyProcessedEventAction,
    ): Event {
        validate(manuallyProcessedEventAction)

        authorizationService.validateUserIsAuthorizedForSourceApplication(
            authentication,
            manuallyProcessedEventAction.sourceApplicationId,
        )

        return manualEventCreationService.addManuallyProcessedEvent(manuallyProcessedEventAction)
    }

    @PostMapping("events/instance-manually-rejected")
    fun setManuallyRejected(
        authentication: Authentication,
        @RequestBody manuallyRejectedEventAction: ManuallyRejectedEventAction,
    ): Event {
        validate(manuallyRejectedEventAction)

        authorizationService.validateUserIsAuthorizedForSourceApplication(
            authentication,
            manuallyRejectedEventAction.sourceApplicationId,
        )

        return manualEventCreationService.addManuallyRejectedEvent(manuallyRejectedEventAction)
    }

    @PostMapping("events/instance-status-overridden-as-transferred")
    fun setInstanceStatusTransferredOverride(
        authentication: Authentication,
        @RequestBody instanceStatusTransferredOverrideAction: InstanceStatusTransferredOverrideAction,
    ): Event {
        validate(instanceStatusTransferredOverrideAction)

        authorizationService.validateUserIsAuthorizedForSourceApplication(
            authentication,
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
