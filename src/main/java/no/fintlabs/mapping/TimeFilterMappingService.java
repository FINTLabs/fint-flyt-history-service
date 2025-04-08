package no.fintlabs.mapping;

import no.fintlabs.model.instance.ActiveTimePeriod;
import no.fintlabs.model.time.ManualTimeFilter;
import no.fintlabs.model.time.OffsetTimeFilter;
import no.fintlabs.model.time.TimeFilter;
import no.fintlabs.repository.filters.TimeQueryFilter;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;
import java.util.Optional;

// TODO 08/04/2025 eivindmorch: Test
@Service
public class TimeFilterMappingService {

    public TimeQueryFilter toQueryFilter(TimeFilter timeFilter) {
        if (Objects.isNull(timeFilter)) {
            return TimeQueryFilter.EMPTY;
        }
        return Optional.ofNullable(timeFilter.getOffset()).map(this::createQueryFilterFromOffsetTimeFilter)
                .or(() -> Optional.ofNullable(timeFilter.getCurrentPeriod()).map(this::createQueryFilterFromCurrentPeriodTimeFilter))
                .or(() -> Optional.ofNullable(timeFilter.getManual()).map(this::createQueryFilterFromManualTimeFilter))
                .orElse(TimeQueryFilter.EMPTY);
    }

    public TimeQueryFilter createQueryFilterFromOffsetTimeFilter(OffsetTimeFilter offsetTimeFilter) {
        OffsetDateTime currentTime = OffsetDateTime.now();
        return TimeQueryFilter.builder()
                .latestStatusTimestampMin(
                        currentTime
                                .minusHours(Optional.ofNullable(offsetTimeFilter.getHours()).orElse(0))
                                .minusMinutes(Optional.ofNullable(offsetTimeFilter.getMinutes()).orElse(0))
                )
                .latestStatusTimestampMax(currentTime)
                .build();
    }

    public TimeQueryFilter createQueryFilterFromCurrentPeriodTimeFilter(ActiveTimePeriod currentTimePeriod) {
        OffsetDateTime currentTime = OffsetDateTime.now(ZoneId.of("Z"));
        return switch (currentTimePeriod) {
            case TODAY -> TimeQueryFilter
                    .builder()
                    .latestStatusTimestampMin(getStartOfDay(currentTime))
                    .latestStatusTimestampMax(getEndOfDay(currentTime))
                    .build();
            case THIS_WEEK -> TimeQueryFilter
                    .builder()
                    .latestStatusTimestampMin(getStartOfDay(
                            currentTime.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    ))
                    .latestStatusTimestampMax(getEndOfDay(
                            currentTime.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                    ))
                    .build();
            case THIS_MONTH -> TimeQueryFilter
                    .builder()
                    .latestStatusTimestampMin(getStartOfDay(
                            currentTime.with(TemporalAdjusters.firstDayOfMonth())
                    ))
                    .latestStatusTimestampMax(getEndOfDay(
                            currentTime.with(TemporalAdjusters.lastDayOfMonth())
                    ))
                    .build();
            case THIS_YEAR -> TimeQueryFilter
                    .builder()
                    .latestStatusTimestampMin(getStartOfDay(
                            currentTime.with(TemporalAdjusters.firstDayOfYear())
                    ))
                    .latestStatusTimestampMax(getEndOfDay(
                            currentTime.with(TemporalAdjusters.lastDayOfYear())
                    ))
                    .build();
        };
    }

    private OffsetDateTime getStartOfDay(OffsetDateTime offsetDateTime) {
        return offsetDateTime.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private OffsetDateTime getEndOfDay(OffsetDateTime offsetDateTime) {
        return offsetDateTime.toLocalDate().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    public TimeQueryFilter createQueryFilterFromManualTimeFilter(ManualTimeFilter manualTimeFilter) {
        return TimeQueryFilter
                .builder()
                .latestStatusTimestampMin(manualTimeFilter.getMin())
                .latestStatusTimestampMax(manualTimeFilter.getMax())
                .build();
    }

}
