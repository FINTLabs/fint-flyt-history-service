package no.novari.flyt.history.kafka;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import no.novari.flyt.history.model.SourceApplicationAggregateInstanceId;

@Getter
@EqualsAndHashCode
@Jacksonized
@Builder
public class ArchiveInstanceIdRequestParams implements SourceApplicationAggregateInstanceId {
    private Long sourceApplicationId;
    private String sourceApplicationIntegrationId;
    private String sourceApplicationInstanceId;
}
