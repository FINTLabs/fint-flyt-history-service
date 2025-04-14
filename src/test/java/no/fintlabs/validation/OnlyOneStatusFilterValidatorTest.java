package no.fintlabs.validation;

import no.fintlabs.model.event.EventCategory;
import no.fintlabs.model.instance.InstanceFlowSummariesFilter;
import no.fintlabs.model.instance.InstanceStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OnlyOneStatusFilterValidatorTest {

    @Test
    public void givenNullFilter_thenReturnTrue() {
        OnlyOneStatusFilterValidator onlyOneStatusFilterValidator = new OnlyOneStatusFilterValidator();
        boolean valid = onlyOneStatusFilterValidator.isValid(null, null);
        assertThat(valid).isTrue();
    }

    @Test
    public void givenFilterHasNoStatusesOrLatestStatusEvents_thenReturnTrue() {
        OnlyOneStatusFilterValidator onlyOneStatusFilterValidator = new OnlyOneStatusFilterValidator();
        boolean valid = onlyOneStatusFilterValidator.isValid(
                InstanceFlowSummariesFilter.builder().build(),
                null
        );
        assertThat(valid).isTrue();
    }

    @Test
    public void givenFilterHasStatusesAndNoLatestStatusEvents_thenReturnTrue() {
        OnlyOneStatusFilterValidator onlyOneStatusFilterValidator = new OnlyOneStatusFilterValidator();
        boolean valid = onlyOneStatusFilterValidator.isValid(
                InstanceFlowSummariesFilter
                        .builder()
                        .statuses(List.of(
                                InstanceStatus.IN_PROGRESS,
                                InstanceStatus.ABORTED
                        ))
                        .build(),
                null
        );
        assertThat(valid).isTrue();
    }

    @Test
    public void givenFilterHasLatestStatusEventsAndNoStatuses_thenReturnTrue() {
        OnlyOneStatusFilterValidator onlyOneStatusFilterValidator = new OnlyOneStatusFilterValidator();
        boolean valid = onlyOneStatusFilterValidator.isValid(
                InstanceFlowSummariesFilter
                        .builder()
                        .latestStatusEvents(List.of(
                                EventCategory.INSTANCE_MAPPED,
                                EventCategory.INSTANCE_RECEIVAL_ERROR
                        ))
                        .build(),
                null
        );
        assertThat(valid).isTrue();
    }

    @Test
    public void givenFilterHasStatusesAndLatestStatusEvents_thenReturnTrue() {
        OnlyOneStatusFilterValidator onlyOneStatusFilterValidator = new OnlyOneStatusFilterValidator();
        boolean valid = onlyOneStatusFilterValidator.isValid(
                InstanceFlowSummariesFilter
                        .builder()
                        .statuses(List.of(
                                InstanceStatus.IN_PROGRESS,
                                InstanceStatus.ABORTED
                        ))
                        .latestStatusEvents(List.of(
                                EventCategory.INSTANCE_MAPPED,
                                EventCategory.INSTANCE_RECEIVAL_ERROR
                        ))
                        .build(),
                null
        );
        assertThat(valid).isFalse();
    }

}