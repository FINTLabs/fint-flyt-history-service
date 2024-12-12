package no.fintlabs.repositories.utils;

import lombok.Getter;
import no.fintlabs.model.eventinfo.EventInfo;
import no.fintlabs.model.eventinfo.InstanceStatusEvent;
import no.fintlabs.model.eventinfo.InstanceStorageStatusEvent;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Getter
public enum EventSequence {

    HAPPY_CASE(
            InstanceStatusEvent.INSTANCE_RECEIVED,
            InstanceStorageStatusEvent.INSTANCE_REGISTERED,
            InstanceStatusEvent.INSTANCE_MAPPED,
            InstanceStatusEvent.INSTANCE_READY_FOR_DISPATCH,
            InstanceStatusEvent.INSTANCE_DISPATCHED,
            InstanceStorageStatusEvent.INSTANCE_DELETED
    ),
    RECEIVAL_ERROR(
            InstanceStatusEvent.INSTANCE_RECEIVAL_ERROR
    ),
    REGISTRATION_ERROR(
            InstanceStatusEvent.INSTANCE_RECEIVED,
            InstanceStatusEvent.INSTANCE_REGISTRATION_ERROR
    ),
    MAPPING_ERROR(
            InstanceStatusEvent.INSTANCE_RECEIVED,
            InstanceStorageStatusEvent.INSTANCE_REGISTERED,
            InstanceStatusEvent.INSTANCE_MAPPING_ERROR
    ),
    DISPATCH_ERROR(
            InstanceStatusEvent.INSTANCE_RECEIVED,
            InstanceStorageStatusEvent.INSTANCE_REGISTERED,
            InstanceStatusEvent.INSTANCE_MAPPED,
            InstanceStatusEvent.INSTANCE_READY_FOR_DISPATCH,
            InstanceStatusEvent.INSTANCE_DISPATCHING_ERROR
    ),

    RETRY_SUCCESS_WITHOUT_PREVIOUS_EVENTS(
            InstanceStatusEvent.INSTANCE_REQUESTED_FOR_RETRY,
            InstanceStatusEvent.INSTANCE_MAPPED,
            InstanceStatusEvent.INSTANCE_READY_FOR_DISPATCH,
            InstanceStatusEvent.INSTANCE_DISPATCHED,
            InstanceStorageStatusEvent.INSTANCE_DELETED
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
                            InstanceStatusEvent.INSTANCE_REQUESTED_FOR_RETRY,
                            InstanceStatusEvent.INSTANCE_MAPPING_ERROR
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
                            InstanceStatusEvent.INSTANCE_REQUESTED_FOR_RETRY,
                            InstanceStatusEvent.INSTANCE_MAPPED,
                            InstanceStatusEvent.INSTANCE_READY_FOR_DISPATCH,
                            InstanceStatusEvent.INSTANCE_DISPATCHING_ERROR
                    ),
                    RETRY_SUCCESS_WITHOUT_PREVIOUS_EVENTS.getOrder()
            ))
    );

    private final List<EventInfo> order;

    EventSequence(EventInfo... order) {
        this.order = Arrays.stream(order).toList();
    }

    private static EventInfo[] combineOrder(List<List<EventInfo>> orders) {
        return orders.stream().flatMap(Collection::stream).toArray(EventInfo[]::new);
    }

}
