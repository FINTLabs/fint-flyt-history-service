package no.fintlabs.model.event;

import lombok.*;
import no.fintlabs.flyt.kafka.headers.InstanceFlowHeaders;
import no.fintlabs.repository.entities.ErrorEntity;

import java.time.OffsetDateTime;
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
    private Collection<ErrorEntity> errors;
}
