package no.novari.flyt.history.model.action;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.jackson.Jacksonized;
import no.novari.flyt.history.model.SourceApplicationAggregateInstanceId;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Jacksonized
@Builder
@Getter
@Setter
public class InstanceStatusTransferredOverrideAction implements SourceApplicationAggregateInstanceId {
    @NotNull
    private Long sourceApplicationId;
    @NotEmpty
    private String sourceApplicationInstanceId;
    @NotEmpty
    private String sourceApplicationIntegrationId;
}
