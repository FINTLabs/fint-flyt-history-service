package no.novari.flyt.history.mapping;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import no.novari.flyt.history.model.instance.ActiveTimePeriod;
import no.novari.flyt.history.model.time.ManualTimeFilter;
import no.novari.flyt.history.model.time.OffsetTimeFilter;
import no.novari.flyt.history.model.time.TimeFilter;
import no.novari.flyt.history.repository.filters.TimeQueryFilter;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class TimeFilterMappingService {

    private final Clock clock;
    private final Validator validator;

    public TimeFilterMappingService(Clock clock, Validator validator) {
        this.clock = clock;
        this.validator = validator;
    }

    public TimeQueryFilter toQueryFilter(TimeFilter timeFilter, ZoneId zoneId) {
        Set<ConstraintViolation<TimeFilter>> validate = validator.validate(timeFilter);
        if (!validate.isEmpty()) {
            throw new IllegalArgumentException(validate.toString());
        }
        if (zoneId == null) {
            throw new IllegalArgumentException("Zone id must not be null");
        }

        if (Objects.isNull(timeFilter)) {
            return TimeQueryFilter.EMPTY;
        }
        return Optional.ofNullable(timeFilter.getOffset()).map(this::createQueryFilterFromOffsetTimeFilter)
                .or(() -> Optional.ofNullable(timeFilter.getCurrentPeriod())
                        .map(currentPeriod -> createQueryFilterFromCurrentPeriodTimeFilter(currentPeriod, zoneId)))
                .or(() -> Optional.ofNullable(timeFilter.getManual()).map(this::createQueryFilterFromManualTimeFilter))
                .orElse(TimeQueryFilter.EMPTY);
    }

    private TimeQueryFilter createQueryFilterFromOffsetTimeFilter(OffsetTimeFilter offsetTimeFilter) {
        OffsetDateTime currentTime = OffsetDateTime.now(clock);
        return TimeQueryFilter.builder()
                .latestStatusTimestampMin(
                        currentTime
                                .minusHours(Optional.ofNullable(offsetTimeFilter.getHours()).orElse(0))
                                .minusMinutes(Optional.ofNullable(offsetTimeFilter.getMinutes()).orElse(0))
                )
                .latestStatusTimestampMax(currentTime)
                .build();
    }

    private TimeQueryFilter createQueryFilterFromCurrentPeriodTimeFilter(ActiveTimePeriod activeTimePeriod, ZoneId zoneId) {
        ZonedDateTime currentZonedTime = OffsetDateTime.now(clock).atZoneSameInstant(zoneId);
        return switch (activeTimePeriod) {
            case TODAY -> TimeQueryFilter
                    .builder()
                    .latestStatusTimestampMin(getStartOfDayInUtc(currentZonedTime))
                    .latestStatusTimestampMax(getEndOfDayInUtc(currentZonedTime))
                    .build();
            case THIS_WEEK -> TimeQueryFilter
                    .builder()
                    .latestStatusTimestampMin(getStartOfDayInUtc(
                            currentZonedTime.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    ))
                    .latestStatusTimestampMax(getEndOfDayInUtc(
                            currentZonedTime.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                    ))
                    .build();
            case THIS_MONTH -> TimeQueryFilter
                    .builder()
                    .latestStatusTimestampMin(getStartOfDayInUtc(
                            currentZonedTime.with(TemporalAdjusters.firstDayOfMonth())
                    ))
                    .latestStatusTimestampMax(getEndOfDayInUtc(
                            currentZonedTime.with(TemporalAdjusters.lastDayOfMonth())
                    ))
                    .build();
            case THIS_YEAR -> TimeQueryFilter
                    .builder()
                    .latestStatusTimestampMin(getStartOfDayInUtc(
                            currentZonedTime.with(TemporalAdjusters.firstDayOfYear())
                    ))
                    .latestStatusTimestampMax(getEndOfDayInUtc(
                            currentZonedTime.with(TemporalAdjusters.lastDayOfYear())
                    ))
                    .build();
        };
    }

    private OffsetDateTime getStartOfDayInUtc(ZonedDateTime zonedDateTime) {
        return OffsetDateTime.of(
                zonedDateTime
                        .toLocalDate()
                        .atStartOfDay(),
                zonedDateTime.getOffset()
        ).withOffsetSameInstant(ZoneOffset.UTC);
    }

    private OffsetDateTime getEndOfDayInUtc(ZonedDateTime zonedDateTime) {
        return OffsetDateTime.of(
                zonedDateTime
                        .toLocalDate()
                        .plusDays(1).atStartOfDay(),
                zonedDateTime.getOffset()
        ).withOffsetSameInstant(ZoneOffset.UTC);
    }

    private TimeQueryFilter createQueryFilterFromManualTimeFilter(ManualTimeFilter manualTimeFilter) {
        return TimeQueryFilter
                .builder()
                .latestStatusTimestampMin(
                        Optional.ofNullable(manualTimeFilter.getMin())
                                .map(t -> t.withOffsetSameInstant(ZoneOffset.UTC))
                                .orElse(null)
                )
                .latestStatusTimestampMax(Optional.ofNullable(manualTimeFilter.getMax())
                        .map(t -> t.withOffsetSameInstant(ZoneOffset.UTC))
                        .orElse(null)
                )
                .build();
    }

}
