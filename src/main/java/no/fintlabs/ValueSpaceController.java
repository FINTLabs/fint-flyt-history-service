package no.fintlabs;

import no.fintlabs.mapping.selectable.ActiveTimePeriodSelectableMappingService;
import no.fintlabs.mapping.selectable.EventCategorySelectableMappingService;
import no.fintlabs.mapping.selectable.InstanceStatusSelectableMappingService;
import no.fintlabs.mapping.selectable.InstanceStorageStatusSelectableMappingService;
import no.fintlabs.model.Selectable;
import no.fintlabs.model.event.EventCategorizationService;
import no.fintlabs.model.event.EventCategory;
import no.fintlabs.model.instance.ActiveTimePeriod;
import no.fintlabs.model.instance.InstanceStatus;
import no.fintlabs.model.instance.InstanceStorageStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Collection;

import static no.fintlabs.resourceserver.UrlPaths.INTERNAL_API;

@RestController
@RequestMapping(INTERNAL_API + "/instance-flow-tracking/value-space")
public class ValueSpaceController {

    private final EventCategorizationService eventCategorizationService;
    private final InstanceStatusSelectableMappingService instanceStatusSelectableMappingService;
    private final InstanceStorageStatusSelectableMappingService instanceStorageStatusSelectableMappingService;
    private final EventCategorySelectableMappingService eventCategorySelectableMappingService;
    private final ActiveTimePeriodSelectableMappingService activeTimePeriodSelectableMappingService;

    public ValueSpaceController(
            EventCategorizationService eventCategorizationService,
            InstanceStatusSelectableMappingService instanceStatusSelectableMappingService,
            InstanceStorageStatusSelectableMappingService instanceStorageStatusSelectableMappingService,
            EventCategorySelectableMappingService eventCategorySelectableMappingService,
            ActiveTimePeriodSelectableMappingService activeTimePeriodSelectableMappingService
    ) {
        this.eventCategorizationService = eventCategorizationService;
        this.instanceStatusSelectableMappingService = instanceStatusSelectableMappingService;
        this.instanceStorageStatusSelectableMappingService = instanceStorageStatusSelectableMappingService;
        this.eventCategorySelectableMappingService = eventCategorySelectableMappingService;
        this.activeTimePeriodSelectableMappingService = activeTimePeriodSelectableMappingService;
    }

    @GetMapping("instance-status/selectables")
    public ResponseEntity<Collection<Selectable<String>>> getInstanceStatusValueSpace() {
        return ResponseEntity.ok(
                Arrays.stream(InstanceStatus.values())
                        .map(instanceStatusSelectableMappingService::toSelectable)
                        .toList()
        );
    }

    @GetMapping("storage-status/selectables")
    public ResponseEntity<Collection<Selectable<String>>> getStorageStatusValueSpace() {
        return ResponseEntity.ok(
                Arrays.stream(InstanceStorageStatus.values())
                        .map(instanceStorageStatusSelectableMappingService::toSelectable)
                        .toList()
        );
    }

    @GetMapping("event-category/selectables")
    public ResponseEntity<Collection<Selectable<String>>> getEventCategoryValueSpace() {
        return ResponseEntity.ok(
                Arrays.stream(EventCategory.values())
                        .map(eventCategorySelectableMappingService::toSelectable)
                        .toList()
        );
    }

    @GetMapping("instance-status-event-category/selectables")
    public ResponseEntity<Collection<Selectable<String>>> getInstanceStatusEventCategoryValueSpace() {
        return ResponseEntity.ok(
                eventCategorizationService.getInstanceStatusCategories().stream()
                        .map(eventCategorySelectableMappingService::toSelectable)
                        .toList()
        );
    }

    @GetMapping("time/current-period/selectables")
    public ResponseEntity<Collection<Selectable<String>>> getTimeCurrentPeriodValueSpace() {
        return ResponseEntity.ok(
                Arrays.stream(ActiveTimePeriod.values())
                        .map(activeTimePeriodSelectableMappingService::toSelectable)
                        .toList()
        );
    }

}
