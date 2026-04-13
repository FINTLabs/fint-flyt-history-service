package no.novari.flyt.history

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
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("$INTERNAL_API/instance-flow-tracking/value-space")
class ValueSpaceController(
    private val eventCategorizationService: EventCategorizationService,
    private val instanceStatusSelectableMappingService: InstanceStatusSelectableMappingService,
    private val instanceStorageStatusSelectableMappingService: InstanceStorageStatusSelectableMappingService,
    private val eventCategorySelectableMappingService: EventCategorySelectableMappingService,
    private val activeTimePeriodSelectableMappingService: ActiveTimePeriodSelectableMappingService,
) {
    @GetMapping("instance-status/selectables")
    fun getInstanceStatusValueSpace(): ResponseEntity<Collection<Selectable<String>>> {
        return ResponseEntity.ok(
            InstanceStatus.entries.map(instanceStatusSelectableMappingService::toSelectable),
        )
    }

    @GetMapping("storage-status/selectables")
    fun getStorageStatusValueSpace(): ResponseEntity<Collection<Selectable<String>>> {
        return ResponseEntity.ok(
            InstanceStorageStatus.entries.map(instanceStorageStatusSelectableMappingService::toSelectable),
        )
    }

    @GetMapping("event-category/selectables")
    fun getEventCategoryValueSpace(): ResponseEntity<Collection<Selectable<String>>> {
        return ResponseEntity.ok(
            EventCategory.entries.map(eventCategorySelectableMappingService::toSelectable),
        )
    }

    @GetMapping("instance-status-event-category/selectables")
    fun getInstanceStatusEventCategoryValueSpace(): ResponseEntity<Collection<Selectable<String>>> {
        return ResponseEntity.ok(
            eventCategorizationService.instanceStatusCategories.map(
                eventCategorySelectableMappingService::toSelectable,
            ),
        )
    }

    @GetMapping("time/current-period/selectables")
    fun getTimeCurrentPeriodValueSpace(): ResponseEntity<Collection<Selectable<String>>> {
        return ResponseEntity.ok(
            ActiveTimePeriod.entries.map(activeTimePeriodSelectableMappingService::toSelectable),
        )
    }
}
