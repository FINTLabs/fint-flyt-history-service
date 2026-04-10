package no.novari.flyt.history.model.event

import no.novari.flyt.history.model.instance.InstanceStatus
import no.novari.flyt.history.model.instance.InstanceStorageStatus
import no.novari.flyt.history.repository.filters.EventNamesPerInstanceStatus
import org.springframework.stereotype.Service

@Service
class EventCategorizationService {
    val eventNamesPerInstanceStatus: EventNamesPerInstanceStatus =
        EventNamesPerInstanceStatus
            .builder()
            .inProgressStatusEventNames(getEventNamesByInstanceStatuses(InstanceStatus.IN_PROGRESS))
            .transferredStatusEventNames(getEventNamesByInstanceStatuses(InstanceStatus.TRANSFERRED))
            .abortedStatusEventNames(getEventNamesByInstanceStatuses(InstanceStatus.ABORTED))
            .failedStatusEventNames(getEventNamesByInstanceStatuses(InstanceStatus.FAILED))
            .allStatusEventNames(allInstanceStatusEventNames)
            .build()

    private val categoryByName = EventCategory.entries.associateBy(EventCategory::eventName)

    fun getCategoriesByInstanceStorageStatuses(
        instanceStorageStatuses: Collection<InstanceStorageStatus>,
    ): Set<EventCategory> {
        return EventCategory.entries
            .filter { it.instanceStorageStatus in instanceStorageStatuses }
            .toSet()
    }

    fun getEventNamesByInstanceStorageStatuses(
        instanceStorageStatuses: Collection<InstanceStorageStatus>,
    ): Set<String> {
        return getCategoriesByInstanceStorageStatuses(instanceStorageStatuses)
            .mapTo(linkedSetOf(), EventCategory::eventName)
    }

    fun getCategoriesByInstanceStatuses(instanceStatuses: Collection<InstanceStatus>): Set<EventCategory> {
        return EventCategory.entries
            .filter { it.instanceStatus in instanceStatuses }
            .toSet()
    }

    fun getEventNamesByInstanceStatuses(vararg instanceStatuses: InstanceStatus): Set<String> {
        return getEventNamesByInstanceStatuses(instanceStatuses.asList())
    }

    fun getEventNamesByInstanceStatuses(instanceStatuses: Collection<InstanceStatus>): Set<String> {
        return getCategoriesByInstanceStatuses(instanceStatuses)
            .mapTo(linkedSetOf(), EventCategory::eventName)
    }

    fun getCategoryByEventName(name: String): EventCategory? = categoryByName[name]

    fun getStatusByEventName(name: String): InstanceStatus {
        val category = requireNotNull(getCategoryByEventName(name)) { "No category with name=$name" }
        return requireNotNull(category.instanceStatus) { "Category=$name is not an instance status category" }
    }

    fun getStorageStatusByEventName(name: String): InstanceStorageStatus {
        val category = requireNotNull(getCategoryByEventName(name)) { "No category with name=$name" }
        return requireNotNull(
            category.instanceStorageStatus,
        ) { "Category=$name is not an instance storage status category" }
    }

    val instanceStatusCategories: Set<EventCategory>
        get() = EventCategory.entries.filter { it.instanceStatus != null }.toSet()

    val allInstanceStatusEventNames: Set<String>
        get() = instanceStatusCategories.mapTo(linkedSetOf(), EventCategory::eventName)

    val instanceStorageStatusCategories: Set<EventCategory>
        get() = EventCategory.entries.filter { it.instanceStorageStatus != null }.toSet()

    val allInstanceStorageStatusEventNames: Set<String>
        get() = instanceStorageStatusCategories.mapTo(linkedSetOf(), EventCategory::eventName)
}
