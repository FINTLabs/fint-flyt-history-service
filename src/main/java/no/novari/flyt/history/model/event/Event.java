package no.novari.flyt.history.model.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import no.novari.flyt.history.repository.entities.ErrorEntity;
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;

@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class Event {
    private InstanceFlowHeaders instanceFlowHeaders;
    private EventCategory category;
    private OffsetDateTime timestamp;
    private EventType type;
    private String applicationId;
    @Builder.Default
    private Collection<ErrorEntity> errors = new ArrayList<>();
}
