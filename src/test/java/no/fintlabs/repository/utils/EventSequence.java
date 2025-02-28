package no.fintlabs.repository.utils;

import lombok.Getter;
import no.fintlabs.model.event.EventCategory;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Getter
public enum EventSequence {

    HAPPY_CASE(
            EventCategory.INSTANCE_RECEIVED,
            EventCategory.INSTANCE_REGISTERED,
            EventCategory.INSTANCE_MAPPED,
            EventCategory.INSTANCE_READY_FOR_DISPATCH,
            EventCategory.INSTANCE_DISPATCHED,
            EventCategory.INSTANCE_DELETED
    ),
    RECEIVAL_ERROR(
            EventCategory.INSTANCE_RECEIVAL_ERROR
    ),
    REGISTRATION_ERROR(
            EventCategory.INSTANCE_RECEIVED,
            EventCategory.INSTANCE_REGISTRATION_ERROR
    ),
    MAPPING_ERROR(
            EventCategory.INSTANCE_RECEIVED,
            EventCategory.INSTANCE_REGISTERED,
            EventCategory.INSTANCE_MAPPING_ERROR
    ),
    DISPATCH_ERROR(
            EventCategory.INSTANCE_RECEIVED,
            EventCategory.INSTANCE_REGISTERED,
            EventCategory.INSTANCE_MAPPED,
            EventCategory.INSTANCE_READY_FOR_DISPATCH,
            EventCategory.INSTANCE_DISPATCHING_ERROR
    ),

    RETRY_SUCCESS_WITHOUT_PREVIOUS_EVENTS(
            EventCategory.INSTANCE_REQUESTED_FOR_RETRY,
            EventCategory.INSTANCE_MAPPED,
            EventCategory.INSTANCE_READY_FOR_DISPATCH,
            EventCategory.INSTANCE_DISPATCHED,
            EventCategory.INSTANCE_DELETED
    ),
    MAPPING_ERROR_RETRY_SUCCESS(
            combineOrder(List.of(
                    MAPPING_ERROR.getOrder(),
                    RETRY_SUCCESS_WITHOUT_PREVIOUS_EVENTS.getOrder()
            ))
    ),
    MAPPING_ERROR_RETRY_MAPPING_ERROR_RETRY_SUCCESS(
            combineOrder(List.of(
                    MAPPING_ERROR.getOrder(),
                    List.of(
                            EventCategory.INSTANCE_REQUESTED_FOR_RETRY,
                            EventCategory.INSTANCE_MAPPING_ERROR
                    ),
                    RETRY_SUCCESS_WITHOUT_PREVIOUS_EVENTS.getOrder()
            ))
    ),
    DISPATCH_ERROR_RETRY_SUCCESS(
            combineOrder(List.of(
                    DISPATCH_ERROR.getOrder(),
                    RETRY_SUCCESS_WITHOUT_PREVIOUS_EVENTS.getOrder()
            ))
    ),
    DISPATCH_ERROR_RETRY_DISPATCH_ERROR_RETRY_SUCCESS(
            combineOrder(List.of(
                    DISPATCH_ERROR.getOrder(),
                    List.of(
                            EventCategory.INSTANCE_REQUESTED_FOR_RETRY,
                            EventCategory.INSTANCE_MAPPED,
                            EventCategory.INSTANCE_READY_FOR_DISPATCH,
                            EventCategory.INSTANCE_DISPATCHING_ERROR
                    ),
                    RETRY_SUCCESS_WITHOUT_PREVIOUS_EVENTS.getOrder()
            ))
    );

    private final List<EventCategory> order;

    EventSequence(EventCategory... order) {
        this.order = Arrays.stream(order).toList();
    }

    private static EventCategory[] combineOrder(List<List<EventCategory>> orders) {
        return orders.stream().flatMap(Collection::stream).toArray(EventCategory[]::new);
    }

}
