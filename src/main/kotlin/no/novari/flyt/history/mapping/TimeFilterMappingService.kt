package no.novari.flyt.history.mapping

import jakarta.validation.Validator
import no.novari.flyt.history.model.instance.ActiveTimePeriod
import no.novari.flyt.history.model.time.ManualTimeFilter
import no.novari.flyt.history.model.time.OffsetTimeFilter
import no.novari.flyt.history.model.time.TimeFilter
import no.novari.flyt.history.repository.filters.TimeQueryFilter
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.DayOfWeek
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

@Service
class TimeFilterMappingService(
    private val clock: Clock,
    private val validator: Validator,
) {
    fun toQueryFilter(
        timeFilter: TimeFilter?,
        zoneId: ZoneId,
    ): TimeQueryFilter {
        if (timeFilter == null) {
            return TimeQueryFilter.EMPTY
        }

        val violations = validator.validate(timeFilter)
        require(violations.isEmpty()) { violations.toString() }

        return when {
            timeFilter.offset != null -> {
                createQueryFilterFromOffsetTimeFilter(timeFilter.offset)
            }

            timeFilter.currentPeriod != null -> {
                createQueryFilterFromCurrentPeriodTimeFilter(
                    timeFilter.currentPeriod,
                    zoneId,
                )
            }

            timeFilter.manual != null -> {
                createQueryFilterFromManualTimeFilter(timeFilter.manual)
            }

            else -> {
                TimeQueryFilter.EMPTY
            }
        }
    }

    private fun createQueryFilterFromOffsetTimeFilter(offsetTimeFilter: OffsetTimeFilter): TimeQueryFilter {
        val currentTime = OffsetDateTime.now(clock)
        return TimeQueryFilter
            .builder()
            .latestStatusTimestampMin(
                currentTime
                    .minusHours(offsetTimeFilter.hours?.toLong() ?: 0)
                    .minusMinutes(offsetTimeFilter.minutes?.toLong() ?: 0),
            ).latestStatusTimestampMax(currentTime)
            .build()
    }

    private fun createQueryFilterFromCurrentPeriodTimeFilter(
        activeTimePeriod: ActiveTimePeriod,
        zoneId: ZoneId,
    ): TimeQueryFilter {
        val currentZonedTime = OffsetDateTime.now(clock).atZoneSameInstant(zoneId)

        return when (activeTimePeriod) {
            ActiveTimePeriod.TODAY -> {
                TimeQueryFilter
                    .builder()
                    .latestStatusTimestampMin(getStartOfDayInUtc(currentZonedTime))
                    .latestStatusTimestampMax(getEndOfDayInUtc(currentZonedTime))
                    .build()
            }

            ActiveTimePeriod.THIS_WEEK -> {
                TimeQueryFilter
                    .builder()
                    .latestStatusTimestampMin(
                        getStartOfDayInUtc(currentZonedTime.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))),
                    ).latestStatusTimestampMax(
                        getEndOfDayInUtc(currentZonedTime.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))),
                    ).build()
            }

            ActiveTimePeriod.THIS_MONTH -> {
                TimeQueryFilter
                    .builder()
                    .latestStatusTimestampMin(
                        getStartOfDayInUtc(currentZonedTime.with(TemporalAdjusters.firstDayOfMonth())),
                    ).latestStatusTimestampMax(
                        getEndOfDayInUtc(currentZonedTime.with(TemporalAdjusters.lastDayOfMonth())),
                    ).build()
            }

            ActiveTimePeriod.THIS_YEAR -> {
                TimeQueryFilter
                    .builder()
                    .latestStatusTimestampMin(
                        getStartOfDayInUtc(currentZonedTime.with(TemporalAdjusters.firstDayOfYear())),
                    ).latestStatusTimestampMax(
                        getEndOfDayInUtc(currentZonedTime.with(TemporalAdjusters.lastDayOfYear())),
                    ).build()
            }
        }
    }

    private fun getStartOfDayInUtc(zonedDateTime: ZonedDateTime): OffsetDateTime {
        return OffsetDateTime
            .of(zonedDateTime.toLocalDate().atStartOfDay(), zonedDateTime.offset)
            .withOffsetSameInstant(ZoneOffset.UTC)
    }

    private fun getEndOfDayInUtc(zonedDateTime: ZonedDateTime): OffsetDateTime {
        return OffsetDateTime
            .of(zonedDateTime.toLocalDate().plusDays(1).atStartOfDay(), zonedDateTime.offset)
            .withOffsetSameInstant(ZoneOffset.UTC)
    }

    private fun createQueryFilterFromManualTimeFilter(manualTimeFilter: ManualTimeFilter): TimeQueryFilter {
        return TimeQueryFilter
            .builder()
            .latestStatusTimestampMin(manualTimeFilter.min?.withOffsetSameInstant(ZoneOffset.UTC))
            .latestStatusTimestampMax(manualTimeFilter.max?.withOffsetSameInstant(ZoneOffset.UTC))
            .build()
    }
}
