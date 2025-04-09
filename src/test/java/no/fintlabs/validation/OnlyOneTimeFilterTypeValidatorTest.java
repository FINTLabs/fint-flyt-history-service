package no.fintlabs.validation;

import no.fintlabs.model.instance.ActiveTimePeriod;
import no.fintlabs.model.time.ActiveTimePeriodFilter;
import no.fintlabs.model.time.ManualTimeFilter;
import no.fintlabs.model.time.OffsetTimeFilter;
import no.fintlabs.model.time.TimeFilter;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OnlyOneTimeFilterTypeValidatorTest {

    @Test
    public void givenAllTimeFilters_shouldReturnFalse() {
        boolean valid = new OnlyOneTimeFilterTypeValidator().isValid(
                TimeFilter
                        .builder()
                        .offset(mock(OffsetTimeFilter.class))
                        .currentPeriod(
                                ActiveTimePeriodFilter
                                        .builder()
                                        .activeTimePeriod(ActiveTimePeriod.TODAY)
                                        .zoneId(ZoneId.of("CET"))
                                        .build()
                        )
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
                        .currentPeriod(
                                ActiveTimePeriodFilter
                                        .builder()
                                        .activeTimePeriod(ActiveTimePeriod.TODAY)
                                        .zoneId(ZoneId.of("CET"))
                                        .build()
                        )
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
                        .currentPeriod(
                                ActiveTimePeriodFilter
                                        .builder()
                                        .activeTimePeriod(ActiveTimePeriod.TODAY)
                                        .zoneId(ZoneId.of("CET"))
                                        .build()
                        )
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

}