package no.fintlabs.model;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@EqualsAndHashCode
@Jacksonized
@Builder
public class ArchiveInstanceIdRequestParams {
    private Long sourceApplicationId;
    private String sourceApplicationInstanceId;
}
