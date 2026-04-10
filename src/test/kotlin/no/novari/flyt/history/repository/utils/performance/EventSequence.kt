package no.novari.flyt.history.repository.utils.performance

import no.novari.flyt.history.model.event.EventCategory

enum class EventSequence(
    val order: List<EventCategory>,
) {
    HAPPY_CASE(
        listOf(
            EventCategory.INSTANCE_RECEIVED,
            EventCategory.INSTANCE_REGISTERED,
            EventCategory.INSTANCE_MAPPED,
            EventCategory.INSTANCE_READY_FOR_DISPATCH,
            EventCategory.INSTANCE_DISPATCHED,
            EventCategory.INSTANCE_DELETED,
        ),
    ),
    RECEIVAL_ERROR(
        listOf(
            EventCategory.INSTANCE_RECEIVAL_ERROR,
        ),
    ),
    REGISTRATION_ERROR(
        listOf(
            EventCategory.INSTANCE_RECEIVED,
            EventCategory.INSTANCE_REGISTRATION_ERROR,
        ),
    ),
    MAPPING_ERROR(
        listOf(
            EventCategory.INSTANCE_RECEIVED,
            EventCategory.INSTANCE_REGISTERED,
            EventCategory.INSTANCE_MAPPING_ERROR,
        ),
    ),
    DISPATCH_ERROR(
        listOf(
            EventCategory.INSTANCE_RECEIVED,
            EventCategory.INSTANCE_REGISTERED,
            EventCategory.INSTANCE_MAPPED,
            EventCategory.INSTANCE_READY_FOR_DISPATCH,
            EventCategory.INSTANCE_DISPATCHING_ERROR,
        ),
    ),
    RETRY_SUCCESS_WITHOUT_PREVIOUS_EVENTS(
        listOf(
            EventCategory.INSTANCE_REQUESTED_FOR_RETRY,
            EventCategory.INSTANCE_MAPPED,
            EventCategory.INSTANCE_READY_FOR_DISPATCH,
            EventCategory.INSTANCE_DISPATCHED,
            EventCategory.INSTANCE_DELETED,
        ),
    ),
    MAPPING_ERROR_RETRY_SUCCESS(
        combinedEventSequence(
            listOf(
                MAPPING_ERROR.order,
                RETRY_SUCCESS_WITHOUT_PREVIOUS_EVENTS.order,
            ),
        ),
    ),
    MAPPING_ERROR_RETRY_MAPPING_ERROR_RETRY_SUCCESS(
        combinedEventSequence(
            listOf(
                MAPPING_ERROR.order,
                listOf(
                    EventCategory.INSTANCE_REQUESTED_FOR_RETRY,
                    EventCategory.INSTANCE_MAPPING_ERROR,
                ),
                RETRY_SUCCESS_WITHOUT_PREVIOUS_EVENTS.order,
            ),
        ),
    ),
    DISPATCH_ERROR_RETRY_SUCCESS(
        combinedEventSequence(
            listOf(
                DISPATCH_ERROR.order,
                RETRY_SUCCESS_WITHOUT_PREVIOUS_EVENTS.order,
            ),
        ),
    ),
    DISPATCH_ERROR_RETRY_DISPATCH_ERROR_RETRY_SUCCESS(
        combinedEventSequence(
            listOf(
                DISPATCH_ERROR.order,
                listOf(
                    EventCategory.INSTANCE_REQUESTED_FOR_RETRY,
                    EventCategory.INSTANCE_MAPPED,
                    EventCategory.INSTANCE_READY_FOR_DISPATCH,
                    EventCategory.INSTANCE_DISPATCHING_ERROR,
                ),
                RETRY_SUCCESS_WITHOUT_PREVIOUS_EVENTS.order,
            ),
        ),
    ),
}

private fun combinedEventSequence(orders: List<List<EventCategory>>): List<EventCategory> = orders.flatten()
