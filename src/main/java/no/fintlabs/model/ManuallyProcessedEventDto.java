package no.fintlabs.model;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Getter
@Setter
public class ManuallyProcessedEventDto implements ManualEventDto {
    @NotNull
    private Long sourceApplicationId;
    @NotEmpty
    private String sourceApplicationInstanceId;
    @NotEmpty
    private String sourceApplicationIntegrationId;
    @NotEmpty
    private String archiveInstanceId;
}
