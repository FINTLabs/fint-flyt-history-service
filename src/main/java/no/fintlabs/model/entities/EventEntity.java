package no.fintlabs.model.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.extern.jackson.Jacksonized;
import no.fintlabs.model.eventinfo.EventType;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.Collection;

@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Jacksonized
@Entity
@Table(
        name = "event",
        indexes = {
                @Index(name = "sourceApplicationIndex",
                        columnList = "sourceApplicationId, sourceApplicationIntegrationId, sourceApplicationInstanceId"
                ),
                @Index(name = "timestampIndex", columnList = "timestamp")
        })
public class EventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private long id;

    @Embedded
    private InstanceFlowHeadersEmbeddable instanceFlowHeaders;

    private String name;

    private OffsetDateTime timestamp;

    @Enumerated(EnumType.STRING)
    private EventType type;

    private String applicationId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "event_id")
    private Collection<ErrorEntity> errors;

}
