package no.fintlabs.repository.entities;

import lombok.*;

import javax.persistence.*;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@EqualsAndHashCode
@Builder(toBuilder = true)
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class InstanceFlowHeadersEmbeddable {
    private Long sourceApplicationId;
    private String sourceApplicationIntegrationId;
    private String sourceApplicationInstanceId;

    @ElementCollection
    @CollectionTable(
            name = "file_id",
            joinColumns = @JoinColumn(name = "event_id")
    )
    @Column(name = "file_id")
    private List<UUID> fileIds;

    private UUID correlationId;
    private Long integrationId;
    private Long instanceId;
    private Long configurationId;

    private String archiveInstanceId;
}
