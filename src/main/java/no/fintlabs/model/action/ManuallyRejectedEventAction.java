package no.fintlabs.model.action;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.jackson.Jacksonized;
import no.fintlabs.model.SourceApplicationAggregateInstanceId;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Jacksonized
@Builder
@Getter
@Setter
public class ManuallyRejectedEventAction implements SourceApplicationAggregateInstanceId {
    @NotNull
    private Long sourceApplicationId;
    @NotEmpty
    private String sourceApplicationInstanceId;
    @NotEmpty
    private String sourceApplicationIntegrationId;
}
