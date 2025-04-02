package no.fintlabs.repository.utils.instanceflowsummary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import no.fintlabs.repository.entities.EventEntity;
import no.fintlabs.repository.projections.InstanceFlowSummaryProjection;

import java.util.List;

@Getter
@AllArgsConstructor
public class EventEntitiesAndExpectedSummary {
    private final List<EventEntity> eventEntities;
    private final InstanceFlowSummaryProjection expectedSummary;
}
