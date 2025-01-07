package no.fintlabs.repository.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.extern.jackson.Jacksonized;
import no.fintlabs.model.event.EventType;

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
// TODO 15/12/2024 eivindmorch: Sjekk EXPLAIN -- Bruker kun sourceApplicationAggregateIdAndNameAndTimestampIndex og sourceApplicationAggregateIdAndNameIndex
@Table(
        name = "event",
        indexes = {
                @Index(name = "sourceApplicationAggregateIdAndNameIndex",
                        columnList = "sourceApplicationId, sourceApplicationIntegrationId, sourceApplicationInstanceId, name"
                ),
                @Index(name = "sourceApplicationAggregateIdAndNameAndTimestampIndex",
                        columnList = "sourceApplicationId, sourceApplicationIntegrationId, sourceApplicationInstanceId, name, timestamp"
                ),
                @Index(name = "timestampIndex",
                        columnList = "timestamp"
                ),
                @Index(name = "nameIndex",
                        columnList = "name"
                )
        })
public class EventEntity {

    // TODO 20/12/2024 eivindmorch: Identity or sequence? How is migration done?
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
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
