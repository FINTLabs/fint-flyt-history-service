package no.fintlabs.model.action;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.jackson.Jacksonized;
import no.fintlabs.model.SourceApplicationAggregateInstanceId;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Jacksonized
@Builder
@Getter
@Setter
public class ManuallyProcessedEventAction implements SourceApplicationAggregateInstanceId {
    @NotNull
    private Long sourceApplicationId;
    @NotEmpty
    private String sourceApplicationIntegrationId;
    @NotEmpty
    private String sourceApplicationInstanceId;
    @NotEmpty
    private String archiveInstanceId;
}
