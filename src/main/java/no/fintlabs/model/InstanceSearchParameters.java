package no.fintlabs.model;

import java.util.List;
import java.util.Optional;

public record InstanceSearchParameters(Optional<List<Long>> integrationIds, Optional<List<EventType>> eventTypes) {
}
