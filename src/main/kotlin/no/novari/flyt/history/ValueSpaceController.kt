package no.novari.flyt.history

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import no.novari.flyt.history.mapping.selectable.ActiveTimePeriodSelectableMappingService
import no.novari.flyt.history.mapping.selectable.EventCategorySelectableMappingService
import no.novari.flyt.history.mapping.selectable.InstanceStatusSelectableMappingService
import no.novari.flyt.history.mapping.selectable.InstanceStorageStatusSelectableMappingService
import no.novari.flyt.history.model.Selectable
import no.novari.flyt.history.model.event.EventCategorizationService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.instance.ActiveTimePeriod
import no.novari.flyt.history.model.instance.InstanceStatus
import no.novari.flyt.history.model.instance.InstanceStorageStatus
import no.novari.flyt.webresourceserver.UrlPaths.INTERNAL_API
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("$INTERNAL_API/instance-flow-tracking/value-space")
@Tag(name = "Value spaces", description = "Selectable values for instance-flow filters.")
class ValueSpaceController(
    private val eventCategorizationService: EventCategorizationService,
    private val instanceStatusSelectableMappingService: InstanceStatusSelectableMappingService,
    private val instanceStorageStatusSelectableMappingService: InstanceStorageStatusSelectableMappingService,
    private val eventCategorySelectableMappingService: EventCategorySelectableMappingService,
    private val activeTimePeriodSelectableMappingService: ActiveTimePeriodSelectableMappingService,
) {
    @GetMapping("instance-status/selectables")
    @Operation(summary = "List selectable instance statuses")
    fun getInstanceStatusValueSpace(): Collection<Selectable<String>> =
        InstanceStatus.entries.map(instanceStatusSelectableMappingService::toSelectable)

    @GetMapping("storage-status/selectables")
    @Operation(summary = "List selectable storage statuses")
    fun getStorageStatusValueSpace(): Collection<Selectable<String>> =
        InstanceStorageStatus.entries.map(instanceStorageStatusSelectableMappingService::toSelectable)

    @GetMapping("event-category/selectables")
    @Operation(summary = "List selectable event categories")
    fun getEventCategoryValueSpace(): Collection<Selectable<String>> =
        EventCategory.entries.map(eventCategorySelectableMappingService::toSelectable)

    @GetMapping("instance-status-event-category/selectables")
    @Operation(summary = "List event categories that represent instance statuses")
    fun getInstanceStatusEventCategoryValueSpace(): Collection<Selectable<String>> =
        eventCategorizationService.instanceStatusCategories.map(
            eventCategorySelectableMappingService::toSelectable,
        )

    @GetMapping("time/current-period/selectables")
    @Operation(summary = "List selectable current time periods")
    fun getTimeCurrentPeriodValueSpace(): Collection<Selectable<String>> =
        ActiveTimePeriod.entries.map(activeTimePeriodSelectableMappingService::toSelectable)
}
