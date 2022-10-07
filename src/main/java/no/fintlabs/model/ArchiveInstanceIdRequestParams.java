package no.fintlabs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveInstanceIdRequestParams {
    private Long sourceApplicationId;
    private String sourceApplicationInstanceId;
}
