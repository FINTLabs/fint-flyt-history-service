package no.novari.flyt.history.repository.utils.instanceflowsummary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import no.novari.flyt.history.repository.entities.EventEntity;
import no.novari.flyt.history.repository.projections.InstanceFlowSummaryProjection;

import java.util.List;

@Getter
@AllArgsConstructor
public class EventEntitiesAndExpectedSummary {
    private final List<EventEntity> eventEntities;
    private final InstanceFlowSummaryProjection expectedSummary;
}
