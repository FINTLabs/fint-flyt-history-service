package no.fintlabs.repository.utils.performance;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.List;

@ToString
@Builder
@Getter
public class EventGenerationConfig {
    private final long sourceApplicationId;
    private final String sourceApplicationIntegrationId;
    private final long integrationId;
    private final OffsetDateTime minTimestamp;
    private final OffsetDateTime maxTimestamp;
    private final List<EventSequenceGenerationConfig> eventSequenceGenerationConfigs;
}
