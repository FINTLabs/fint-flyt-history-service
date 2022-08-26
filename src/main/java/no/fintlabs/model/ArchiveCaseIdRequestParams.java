package no.fintlabs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveCaseIdRequestParams {
    private String sourceApplicationId;
    private String sourceApplicationInstanceId;
}
