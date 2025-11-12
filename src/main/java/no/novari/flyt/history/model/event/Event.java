package no.novari.flyt.history.model.event;

import lombok.*;
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders;
import no.novari.flyt.history.repository.entities.ErrorEntity;

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
