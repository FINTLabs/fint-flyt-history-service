package no.novari.flyt.history.mapping;

import no.novari.flyt.history.model.instance.ActiveTimePeriod;
import no.novari.flyt.history.model.time.ManualTimeFilter;
import no.novari.flyt.history.model.time.OffsetTimeFilter;
import no.novari.flyt.history.model.time.TimeFilter;
import no.novari.flyt.history.repository.filters.TimeQueryFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TimeFilterMappingServiceTest {

    Clock clock;
    Validator validator;
    TimeFilterMappingService timeFilterMappingService;

    @BeforeEach
    public void setup() {
        clock = Clock.fixed(
                OffsetDateTime.of(2024, 2, 3, 12, 16, 54, 251200, ZoneOffset.UTC).toInstant(),
                ZoneOffset.UTC
        );
        validator = mock(Validator.class);
        timeFilterMappingService = new TimeFilterMappingService(clock, validator);
    }

    @Test
    public void givenNullTimeFilter_whenToQueryFilter_thenReturnEmptyTimeQueryFilter() {
        when(validator.validate(any())).thenReturn(Set.of());
        TimeQueryFilter queryFilter = timeFilterMappingService.toQueryFilter(
                null,
                ZoneId.of("CET")
        );
        assertThat(queryFilter).isEqualTo(TimeQueryFilter.EMPTY);
    }

    @Test
    public void givenEmptyTimeFilter_whenToQueryFilter_thenReturnEmptyTimeQueryFilter() {
        when(validator.validate(any())).thenReturn(Set.of());
        TimeQueryFilter queryFilter = timeFilterMappingService.toQueryFilter(
                TimeFilter.builder().build(),
                ZoneId.of("CET")
        );
        assertThat(queryFilter).isEqualTo(TimeQueryFilter.EMPTY);
    }

    @Test
    public void givenInvalidTimeFilter_whenToQueryFilter_thenThrowException() {
        when(validator.validate(any())).thenReturn(Set.of(mock(ConstraintViolation.class)));
        assertThrows(IllegalArgumentException.class,
                () -> timeFilterMappingService.toQueryFilter(
                        TimeFilter.builder()
                                .offset(OffsetTimeFilter.builder().build())
                                .manual(ManualTimeFilter.builder().build())
                                .build(),
                        ZoneId.of("CET")
                )
        );
    }

    @Test
    public void givenValidTimeFilterWithOffset_whenToQueryFilter_thenReturnToQueryFilterWithMinAndMax() {
        when(validator.validate(any())).thenReturn(Set.of());
        TimeQueryFilter queryFilter = timeFilterMappingService.toQueryFilter(
                TimeFilter.builder()
                        .offset(
                                OffsetTimeFilter
                                        .builder()
                                        .hours(2)
                                        .minutes(11)
                                        .build()
                        )
                        .build(),
                ZoneId.of("CET")
        );
        assertThat(queryFilter.getLatestStatusTimestampMin()).contains(
                OffsetDateTime.of(2024, 2, 3, 10, 5, 54, 251200, ZoneOffset.UTC)
        );
        assertThat(queryFilter.getLatestStatusTimestampMax()).contains(
                OffsetDateTime.of(2024, 2, 3, 12, 16, 54, 251200, ZoneOffset.UTC)
        );
    }

    @Test
    public void givenValidTimeFilterWithCurrentPeriodToday_whenToQueryFilter_thenReturnToQueryFilterWithMinAndMax() {
        when(validator.validate(any())).thenReturn(Set.of());
        TimeQueryFilter queryFilter = timeFilterMappingService.toQueryFilter(
                TimeFilter.builder()
                        .currentPeriod(ActiveTimePeriod.TODAY)
                        .build(),
                ZoneId.of("CET")
        );
        assertThat(queryFilter.getLatestStatusTimestampMin()).contains(
                OffsetDateTime.of(2024, 2, 2, 23, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(queryFilter.getLatestStatusTimestampMax()).contains(
                OffsetDateTime.of(2024, 2, 3, 23, 0, 0, 0, ZoneOffset.UTC)
        );
    }

    @Test
    public void givenValidTimeFilterWithCurrentPeriodThisWeek_whenToQueryFilter_thenReturnToQueryFilterWithMinAndMax() {
        when(validator.validate(any())).thenReturn(Set.of());
        TimeQueryFilter queryFilter = timeFilterMappingService.toQueryFilter(
                TimeFilter.builder()
                        .currentPeriod(ActiveTimePeriod.THIS_WEEK)
                        .build(),
                ZoneId.of("CET")
        );
        assertThat(queryFilter.getLatestStatusTimestampMin()).contains(
                OffsetDateTime.of(2024, 1, 28, 23, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(queryFilter.getLatestStatusTimestampMax()).contains(
                OffsetDateTime.of(2024, 2, 4, 23, 0, 0, 0, ZoneOffset.UTC)
        );
    }

    @Test
    public void givenValidTimeFilterWithCurrentPeriodThisMonth_whenToQueryFilter_thenReturnToQueryFilterWithMinAndMax() {
        when(validator.validate(any())).thenReturn(Set.of());
        TimeQueryFilter queryFilter = timeFilterMappingService.toQueryFilter(
                TimeFilter.builder()
                        .currentPeriod(ActiveTimePeriod.THIS_MONTH)
                        .build(),
                ZoneId.of("CET")
        );
        assertThat(queryFilter.getLatestStatusTimestampMin()).contains(
                OffsetDateTime.of(2024, 1, 31, 23, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(queryFilter.getLatestStatusTimestampMax()).contains(
                OffsetDateTime.of(2024, 2, 29, 23, 0, 0, 0, ZoneOffset.UTC)
        );
    }

    @Test
    public void givenValidTimeFilterWithCurrentPeriodThisYear_whenToQueryFilter_thenReturnToQueryFilterWithMinAndMax() {
        when(validator.validate(any())).thenReturn(Set.of());
        TimeQueryFilter queryFilter = timeFilterMappingService.toQueryFilter(
                TimeFilter.builder()
                        .currentPeriod(ActiveTimePeriod.THIS_YEAR)
                        .build(),
                ZoneId.of("CET")
        );
        assertThat(queryFilter.getLatestStatusTimestampMin()).contains(
                OffsetDateTime.of(2023, 12, 31, 23, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(queryFilter.getLatestStatusTimestampMax()).contains(
                OffsetDateTime.of(2024, 12, 31, 23, 0, 0, 0, ZoneOffset.UTC)
        );
    }

    @Test
    public void givenValidTimeFilterWithCurrentPeriodTodayAndZoneIdWithWinterTime_whenToQueryFilter_thenReturnToQueryFilterSummertimeAdjustedMinAndMax() {
        TimeFilterMappingService timeFilterMappingServiceWinterTimeInOslo = new TimeFilterMappingService(
                Clock.fixed(
                        OffsetDateTime.of(2024, 2, 3, 12, 16, 54, 251200, ZoneOffset.UTC).toInstant(),
                        ZoneOffset.UTC
                ),
                validator
        );
        when(validator.validate(any())).thenReturn(Set.of());
        TimeQueryFilter queryFilter = timeFilterMappingServiceWinterTimeInOslo.toQueryFilter(
                TimeFilter.builder()
                        .currentPeriod(ActiveTimePeriod.TODAY)
                        .build(),
                ZoneId.of("Europe/Oslo")
        );
        assertThat(queryFilter.getLatestStatusTimestampMin()).contains(
                OffsetDateTime.of(2024, 2, 2, 23, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(queryFilter.getLatestStatusTimestampMax()).contains(
                OffsetDateTime.of(2024, 2, 3, 23, 0, 0, 0, ZoneOffset.UTC)
        );
    }

    @Test
    public void givenValidTimeFilterWithCurrentPeriodTodayAndZoneIdWithSummerTime_whenToQueryFilter_thenReturnToQueryFilterSummertimeAdjustedMinAndMax() {
        TimeFilterMappingService timeFilterMappingServiceSummerTimeInOslo = new TimeFilterMappingService(
                Clock.fixed(
                        OffsetDateTime.of(2024, 7, 3, 12, 16, 54, 251200, ZoneOffset.UTC).toInstant(),
                        ZoneOffset.UTC
                ),
                validator
        );
        when(validator.validate(any())).thenReturn(Set.of());
        TimeQueryFilter queryFilter = timeFilterMappingServiceSummerTimeInOslo.toQueryFilter(
                TimeFilter.builder()
                        .currentPeriod(ActiveTimePeriod.TODAY)
                        .build(),
                ZoneId.of("Europe/Oslo")
        );
        assertThat(queryFilter.getLatestStatusTimestampMin()).contains(
                OffsetDateTime.of(2024, 7, 2, 22, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(queryFilter.getLatestStatusTimestampMax()).contains(
                OffsetDateTime.of(2024, 7, 3, 22, 0, 0, 0, ZoneOffset.UTC)
        );
    }

    @Test
    public void givenValidTimeFilterWithManualWithoutMinOrMax_whenToQueryFilter_thenReturnEmptyQueryFilter() {
        when(validator.validate(any())).thenReturn(Set.of());
        TimeQueryFilter queryFilter = timeFilterMappingService.toQueryFilter(
                TimeFilter.builder()
                        .manual(ManualTimeFilter.builder().build())
                        .build(),
                ZoneId.of("CET")
        );
        assertThat(queryFilter.getLatestStatusTimestampMin()).isEmpty();
        assertThat(queryFilter.getLatestStatusTimestampMax()).isEmpty();
    }

    @Test
    public void givenValidTimeFilterWithManualMin_whenToQueryFilter_thenReturnQueryFilterWithMin() {
        when(validator.validate(any())).thenReturn(Set.of());
        TimeQueryFilter queryFilter = timeFilterMappingService.toQueryFilter(
                TimeFilter.builder()
                        .manual(
                                ManualTimeFilter
                                        .builder()
                                        .min(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
                                        .build()
                        )
                        .build(),
                ZoneId.of("CET")
        );
        assertThat(queryFilter.getLatestStatusTimestampMin()).contains(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC));
        assertThat(queryFilter.getLatestStatusTimestampMax()).isEmpty();
    }

    @Test
    public void givenValidTimeFilterWithManualMax_whenToQueryFilter_thenReturnQueryFilterWithMax() {
        when(validator.validate(any())).thenReturn(Set.of());
        TimeQueryFilter queryFilter = timeFilterMappingService.toQueryFilter(
                TimeFilter.builder()
                        .manual(
                                ManualTimeFilter
                                        .builder()
                                        .max(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
                                        .build()
                        )
                        .build(),
                ZoneId.of("CET")
        );
        assertThat(queryFilter.getLatestStatusTimestampMin()).isEmpty();
        assertThat(queryFilter.getLatestStatusTimestampMax()).contains(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    public void givenValidTimeFilterWithManualMinAndMax_whenToQueryFilter_thenReturnQueryFilterWithMinAndMax() {
        when(validator.validate(any())).thenReturn(Set.of());
        TimeQueryFilter queryFilter = timeFilterMappingService.toQueryFilter(
                TimeFilter.builder()
                        .manual(
                                ManualTimeFilter
                                        .builder()
                                        .min(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
                                        .max(OffsetDateTime.of(2024, 1, 2, 12, 0, 0, 0, ZoneOffset.UTC))
                                        .build()
                        )
                        .build(),
                ZoneId.of("CET")
        );
        assertThat(queryFilter.getLatestStatusTimestampMin()).contains(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC));
        assertThat(queryFilter.getLatestStatusTimestampMax()).contains(OffsetDateTime.of(2024, 1, 2, 12, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    public void givenValidTimeFilterWithManualMinAndMaxOfOtherOffsetThanUTC_whenToQueryFilter_thenReturnQueryFilterWithMinAndMaxInUTC() {
        when(validator.validate(any())).thenReturn(Set.of());
        TimeQueryFilter queryFilter = timeFilterMappingService.toQueryFilter(
                TimeFilter.builder()
                        .manual(
                                ManualTimeFilter
                                        .builder()
                                        .min(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.ofHours(-2)))
                                        .max(OffsetDateTime.of(2024, 1, 2, 12, 0, 0, 0, ZoneOffset.ofHours(-4)))
                                        .build()
                        )
                        .build(),
                ZoneId.of("CET")
        );
        assertThat(queryFilter.getLatestStatusTimestampMin()).contains(OffsetDateTime.of(2024, 1, 1, 14, 0, 0, 0, ZoneOffset.UTC));
        assertThat(queryFilter.getLatestStatusTimestampMax()).contains(OffsetDateTime.of(2024, 1, 2, 16, 0, 0, 0, ZoneOffset.UTC));
    }

}