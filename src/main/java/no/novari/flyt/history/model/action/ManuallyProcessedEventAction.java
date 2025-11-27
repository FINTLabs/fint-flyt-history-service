package no.novari.flyt.history.model.action;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.jackson.Jacksonized;
import no.novari.flyt.history.model.SourceApplicationAggregateInstanceId;

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
