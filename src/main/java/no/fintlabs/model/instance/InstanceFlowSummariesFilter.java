package no.fintlabs.model.instance;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import no.fintlabs.model.SourceApplicationIdFilter;
import no.fintlabs.model.SourceApplicationIdFilterBuilder;
import no.fintlabs.model.event.EventCategory;
import no.fintlabs.model.time.TimeFilter;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Collection;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class InstanceFlowSummariesFilter implements SourceApplicationIdFilter<InstanceFlowSummariesFilter> {

    @Valid
    private TimeFilter time;

    private Collection<@NotNull Long> sourceApplicationIds;
    private Collection<@NotBlank String> sourceApplicationIntegrationIds;
    private Collection<@NotBlank String> sourceApplicationInstanceIds;
    private Collection<@NotNull Long> integrationIds;
    private Collection<@NotNull InstanceStatus> statuses;
    private Collection<@NotNull InstanceStorageStatus> storageStatuses;
    private Collection<@NotNull EventCategory> associatedEvents;
    private Collection<@NotBlank String> destinationIds;

    public static class InstanceFlowSummariesFilterBuilder implements SourceApplicationIdFilterBuilder<InstanceFlowSummariesFilter> {

        @JsonProperty
        private Collection<Long> sourceApplicationIds;

        @Override
        public SourceApplicationIdFilterBuilder<InstanceFlowSummariesFilter> sourceApplicationId(
                Collection<Long> sourceApplicationIds
        ) {
            this.sourceApplicationIds = sourceApplicationIds;
            return this;
        }

    }

}
