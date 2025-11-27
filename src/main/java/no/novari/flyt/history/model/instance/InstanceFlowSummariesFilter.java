package no.novari.flyt.history.model.instance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import no.novari.flyt.history.model.event.EventCategory;
import no.novari.flyt.history.model.time.TimeFilter;
import no.novari.flyt.history.validation.OnlyOneStatusFilter;

import java.time.ZoneId;
import java.util.Collection;

@OnlyOneStatusFilter
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode
public class InstanceFlowSummariesFilter {

    private final ZoneId timeZone = ZoneId.of("Europe/Oslo");

    @Valid
    private TimeFilter time;

    private Collection<@NotNull Long> sourceApplicationIds;
    private Collection<@NotBlank String> sourceApplicationIntegrationIds;
    private Collection<@NotBlank String> sourceApplicationInstanceIds;
    private Collection<@NotNull Long> integrationIds;
    private Collection<@NotNull InstanceStatus> statuses;
    private Collection<@NotNull EventCategory> latestStatusEvents;
    private Collection<@NotNull InstanceStorageStatus> storageStatuses;
    private Collection<@NotNull EventCategory> associatedEvents;
    private Collection<@NotBlank String> destinationIds;

}
