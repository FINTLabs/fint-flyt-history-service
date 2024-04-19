package no.fintlabs.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ManualEventDto {
    private Long sourceApplicationId;
    private String sourceApplicationInstanceId;
    private String sourceApplicationIntegrationId;
    private String archiveInstanceId;
}
