package no.fintlabs.model.action;

import lombok.Getter;
import lombok.Setter;
import no.fintlabs.model.SourceApplicationAggregateInstanceId;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

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
