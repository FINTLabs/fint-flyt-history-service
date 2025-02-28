package no.fintlabs.kafka;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import no.fintlabs.model.SourceApplicationAggregateInstanceId;

@Getter
@EqualsAndHashCode
@Jacksonized
@Builder
public class ArchiveInstanceIdRequestParams implements SourceApplicationAggregateInstanceId {
    private Long sourceApplicationId;
    private String sourceApplicationIntegrationId;
    private String sourceApplicationInstanceId;
}
