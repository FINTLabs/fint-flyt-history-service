package no.fintlabs.validation;

import no.fintlabs.model.instance.ActiveTimePeriod;
import no.fintlabs.model.instance.InstanceFlowSummariesFilter;
import no.fintlabs.model.time.ManualTimeFilter;
import no.fintlabs.model.time.OffsetTimeFilter;
import no.fintlabs.model.time.TimeFilter;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OnlyOneTimeFilterTypeValidatorTest {

    @Test
    public void givenAllTimeFilters_shouldReturnFalse() {
        boolean valid = new OnlyOneTimeFilterTypeValidator().isValid(
                TimeFilter
                        .builder()
                        .offset(mock(OffsetTimeFilter.class))
                        .currentPeriod(ActiveTimePeriod.TODAY)
                        .manual(mock(ManualTimeFilter.class))
                        .build(),
                null
        );
        assertThat(valid).isFalse();
    }

    @Test
    public void givenTwoTimeFilters_shouldReturnFalse() {
        boolean valid = new OnlyOneTimeFilterTypeValidator().isValid(
                TimeFilter
                        .builder()
                        .currentPeriod(ActiveTimePeriod.TODAY)
                        .manual(mock(ManualTimeFilter.class))
                        .build(),
                null
        );
        assertThat(valid).isFalse();
    }

    @Test
    public void givenNoTimeFilter_shouldReturnTrue() {
        boolean valid = new OnlyOneTimeFilterTypeValidator().isValid(
                TimeFilter
                        .builder()
                        .build(),
                null
        );
        assertThat(valid).isTrue();
    }

    @Test
    public void givenOnlyOffsetTimeFilter_shouldReturnTrue() {
        boolean valid = new OnlyOneTimeFilterTypeValidator().isValid(
                TimeFilter
                        .builder()
                        .offset(mock(OffsetTimeFilter.class))
                        .build(),
                null
        );
        assertThat(valid).isTrue();
    }

    @Test
    public void givenOnlyCurrentPeriodTimeFilter_shouldReturnTrue() {
        boolean valid = new OnlyOneTimeFilterTypeValidator().isValid(
                TimeFilter
                        .builder()
                        .currentPeriod(ActiveTimePeriod.TODAY)
                        .build(),
                null
        );
        assertThat(valid).isTrue();
    }

    @Test
    public void givenOnlyManualTimeFilter_shouldReturnTrue() {
        boolean valid = new OnlyOneTimeFilterTypeValidator().isValid(
                TimeFilter
                        .builder()
                        .manual(mock(ManualTimeFilter.class))
                        .build(),
                null
        );
        assertThat(valid).isTrue();
    }


    // TODO 06/02/2025 eivindmorch: REMOVE!!
    @Test
    public void a() {
        InstanceFlowSummariesFilter build = InstanceFlowSummariesFilter.builder()
                .time(
                        TimeFilter.builder()
                                .offset(OffsetTimeFilter.builder().minutes(1).build())
                                .build()
                ).build();

        try {
            System.out.println(new ObjectMapper().writer().withDefaultPrettyPrinter().writeValueAsString(build));
        } catch (JsonProcessingException e) {
//            throw new RuntimeException(e);
        }
    }

}