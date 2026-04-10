package no.novari.flyt.history.mapping

import jakarta.validation.ConstraintViolation
import jakarta.validation.Validator
import no.novari.flyt.history.model.instance.ActiveTimePeriod
import no.novari.flyt.history.model.time.ManualTimeFilter
import no.novari.flyt.history.model.time.OffsetTimeFilter
import no.novari.flyt.history.model.time.TimeFilter
import no.novari.flyt.history.repository.filters.TimeQueryFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class TimeFilterMappingServiceTest {
    private lateinit var clock: Clock
    private lateinit var validator: Validator
    private lateinit var timeFilterMappingService: TimeFilterMappingService

    @BeforeEach
    fun setup() {
        clock =
            Clock.fixed(
                OffsetDateTime.of(2024, 2, 3, 12, 16, 54, 251200, ZoneOffset.UTC).toInstant(),
                ZoneOffset.UTC,
            )
        validator = mock()
        timeFilterMappingService = TimeFilterMappingService(clock, validator)
    }

    @Test
    fun `given null time filter when to query filter then return empty time query filter`() {
        whenever(validator.validate(any<Any>())).thenReturn(emptySet())

        val queryFilter =
            timeFilterMappingService.toQueryFilter(
                null,
                ZoneId.of("CET"),
            )

        assertThat(queryFilter).isEqualTo(TimeQueryFilter.EMPTY)
    }

    @Test
    fun `given empty time filter when to query filter then return empty time query filter`() {
        whenever(validator.validate(any<Any>())).thenReturn(emptySet())

        val queryFilter =
            timeFilterMappingService.toQueryFilter(
                TimeFilter.builder().build(),
                ZoneId.of("CET"),
            )

        assertThat(queryFilter).isEqualTo(TimeQueryFilter.EMPTY)
    }

    @Test
    fun `given invalid time filter when to query filter then throw exception`() {
        whenever(validator.validate(any<Any>())).thenReturn(setOf(mock<ConstraintViolation<Any>>()))

        assertThrows<IllegalArgumentException> {
            timeFilterMappingService.toQueryFilter(
                TimeFilter
                    .builder()
                    .offset(OffsetTimeFilter.builder().build())
                    .manual(ManualTimeFilter.builder().build())
                    .build(),
                ZoneId.of("CET"),
            )
        }
    }

    @Test
    fun `given valid time filter with offset when to query filter then return min and max`() {
        whenever(validator.validate(any<Any>())).thenReturn(emptySet())

        val queryFilter =
            timeFilterMappingService.toQueryFilter(
                TimeFilter
                    .builder()
                    .offset(
                        OffsetTimeFilter
                            .builder()
                            .hours(2)
                            .minutes(11)
                            .build(),
                    ).build(),
                ZoneId.of("CET"),
            )

        assertThat(queryFilter.latestStatusTimestampMin)
            .isEqualTo(OffsetDateTime.of(2024, 2, 3, 10, 5, 54, 251200, ZoneOffset.UTC))
        assertThat(queryFilter.latestStatusTimestampMax)
            .isEqualTo(OffsetDateTime.of(2024, 2, 3, 12, 16, 54, 251200, ZoneOffset.UTC))
    }

    @Test
    fun `given current period today when to query filter then return min and max`() {
        whenever(validator.validate(any<Any>())).thenReturn(emptySet())

        val queryFilter =
            timeFilterMappingService.toQueryFilter(
                TimeFilter
                    .builder()
                    .currentPeriod(ActiveTimePeriod.TODAY)
                    .build(),
                ZoneId.of("CET"),
            )

        assertThat(queryFilter.latestStatusTimestampMin)
            .isEqualTo(OffsetDateTime.of(2024, 2, 2, 23, 0, 0, 0, ZoneOffset.UTC))
        assertThat(queryFilter.latestStatusTimestampMax)
            .isEqualTo(OffsetDateTime.of(2024, 2, 3, 23, 0, 0, 0, ZoneOffset.UTC))
    }

    @Test
    fun `given current period this week when to query filter then return min and max`() {
        whenever(validator.validate(any<Any>())).thenReturn(emptySet())

        val queryFilter =
            timeFilterMappingService.toQueryFilter(
                TimeFilter
                    .builder()
                    .currentPeriod(ActiveTimePeriod.THIS_WEEK)
                    .build(),
                ZoneId.of("CET"),
            )

        assertThat(queryFilter.latestStatusTimestampMin)
            .isEqualTo(OffsetDateTime.of(2024, 1, 28, 23, 0, 0, 0, ZoneOffset.UTC))
        assertThat(queryFilter.latestStatusTimestampMax)
            .isEqualTo(OffsetDateTime.of(2024, 2, 4, 23, 0, 0, 0, ZoneOffset.UTC))
    }

    @Test
    fun `given current period this month when to query filter then return min and max`() {
        whenever(validator.validate(any<Any>())).thenReturn(emptySet())

        val queryFilter =
            timeFilterMappingService.toQueryFilter(
                TimeFilter
                    .builder()
                    .currentPeriod(ActiveTimePeriod.THIS_MONTH)
                    .build(),
                ZoneId.of("CET"),
            )

        assertThat(queryFilter.latestStatusTimestampMin)
            .isEqualTo(OffsetDateTime.of(2024, 1, 31, 23, 0, 0, 0, ZoneOffset.UTC))
        assertThat(queryFilter.latestStatusTimestampMax)
            .isEqualTo(OffsetDateTime.of(2024, 2, 29, 23, 0, 0, 0, ZoneOffset.UTC))
    }

    @Test
    fun `given current period this year when to query filter then return min and max`() {
        whenever(validator.validate(any<Any>())).thenReturn(emptySet())

        val queryFilter =
            timeFilterMappingService.toQueryFilter(
                TimeFilter
                    .builder()
                    .currentPeriod(ActiveTimePeriod.THIS_YEAR)
                    .build(),
                ZoneId.of("CET"),
            )

        assertThat(queryFilter.latestStatusTimestampMin)
            .isEqualTo(OffsetDateTime.of(2023, 12, 31, 23, 0, 0, 0, ZoneOffset.UTC))
        assertThat(queryFilter.latestStatusTimestampMax)
            .isEqualTo(OffsetDateTime.of(2024, 12, 31, 23, 0, 0, 0, ZoneOffset.UTC))
    }

    @Test
    fun `given current period today in winter oslo when to query filter then return winter adjusted min and max`() {
        val winterTimeInOslo =
            TimeFilterMappingService(
                Clock.fixed(
                    OffsetDateTime.of(2024, 2, 3, 12, 16, 54, 251200, ZoneOffset.UTC).toInstant(),
                    ZoneOffset.UTC,
                ),
                validator,
            )
        whenever(validator.validate(any<Any>())).thenReturn(emptySet())

        val queryFilter =
            winterTimeInOslo.toQueryFilter(
                TimeFilter
                    .builder()
                    .currentPeriod(ActiveTimePeriod.TODAY)
                    .build(),
                ZoneId.of("Europe/Oslo"),
            )

        assertThat(queryFilter.latestStatusTimestampMin)
            .isEqualTo(OffsetDateTime.of(2024, 2, 2, 23, 0, 0, 0, ZoneOffset.UTC))
        assertThat(queryFilter.latestStatusTimestampMax)
            .isEqualTo(OffsetDateTime.of(2024, 2, 3, 23, 0, 0, 0, ZoneOffset.UTC))
    }

    @Test
    fun `given current period today in summer oslo when to query filter then return summer adjusted min and max`() {
        val summerTimeInOslo =
            TimeFilterMappingService(
                Clock.fixed(
                    OffsetDateTime.of(2024, 7, 3, 12, 16, 54, 251200, ZoneOffset.UTC).toInstant(),
                    ZoneOffset.UTC,
                ),
                validator,
            )
        whenever(validator.validate(any<Any>())).thenReturn(emptySet())

        val queryFilter =
            summerTimeInOslo.toQueryFilter(
                TimeFilter
                    .builder()
                    .currentPeriod(ActiveTimePeriod.TODAY)
                    .build(),
                ZoneId.of("Europe/Oslo"),
            )

        assertThat(queryFilter.latestStatusTimestampMin)
            .isEqualTo(OffsetDateTime.of(2024, 7, 2, 22, 0, 0, 0, ZoneOffset.UTC))
        assertThat(queryFilter.latestStatusTimestampMax)
            .isEqualTo(OffsetDateTime.of(2024, 7, 3, 22, 0, 0, 0, ZoneOffset.UTC))
    }

    @Test
    fun `given valid time filter with manual without min or max when to query filter then return empty query filter`() {
        whenever(validator.validate(any<Any>())).thenReturn(emptySet())

        val queryFilter =
            timeFilterMappingService.toQueryFilter(
                TimeFilter
                    .builder()
                    .manual(ManualTimeFilter.builder().build())
                    .build(),
                ZoneId.of("CET"),
            )

        assertThat(queryFilter.latestStatusTimestampMin).isNull()
        assertThat(queryFilter.latestStatusTimestampMax).isNull()
    }

    @Test
    fun `given valid time filter with manual min when to query filter then return query filter with min`() {
        whenever(validator.validate(any<Any>())).thenReturn(emptySet())

        val queryFilter =
            timeFilterMappingService.toQueryFilter(
                TimeFilter
                    .builder()
                    .manual(
                        ManualTimeFilter
                            .builder()
                            .min(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
                            .build(),
                    ).build(),
                ZoneId.of("CET"),
            )

        assertThat(queryFilter.latestStatusTimestampMin)
            .isEqualTo(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
        assertThat(queryFilter.latestStatusTimestampMax).isNull()
    }

    @Test
    fun `given valid time filter with manual max when to query filter then return query filter with max`() {
        whenever(validator.validate(any<Any>())).thenReturn(emptySet())

        val queryFilter =
            timeFilterMappingService.toQueryFilter(
                TimeFilter
                    .builder()
                    .manual(
                        ManualTimeFilter
                            .builder()
                            .max(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
                            .build(),
                    ).build(),
                ZoneId.of("CET"),
            )

        assertThat(queryFilter.latestStatusTimestampMin).isNull()
        assertThat(queryFilter.latestStatusTimestampMax)
            .isEqualTo(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
    }

    @Test
    fun `given manual min and max when to query filter then return min and max`() {
        whenever(validator.validate(any<Any>())).thenReturn(emptySet())

        val queryFilter =
            timeFilterMappingService.toQueryFilter(
                TimeFilter
                    .builder()
                    .manual(
                        ManualTimeFilter
                            .builder()
                            .min(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
                            .max(OffsetDateTime.of(2024, 1, 2, 12, 0, 0, 0, ZoneOffset.UTC))
                            .build(),
                    ).build(),
                ZoneId.of("CET"),
            )

        assertThat(queryFilter.latestStatusTimestampMin)
            .isEqualTo(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
        assertThat(queryFilter.latestStatusTimestampMax)
            .isEqualTo(OffsetDateTime.of(2024, 1, 2, 12, 0, 0, 0, ZoneOffset.UTC))
    }

    @Test
    fun `given manual min and max with non utc offset then return utc values`() {
        whenever(validator.validate(any<Any>())).thenReturn(emptySet())

        val queryFilter =
            timeFilterMappingService.toQueryFilter(
                TimeFilter
                    .builder()
                    .manual(
                        ManualTimeFilter
                            .builder()
                            .min(OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.ofHours(-2)))
                            .max(OffsetDateTime.of(2024, 1, 2, 12, 0, 0, 0, ZoneOffset.ofHours(-4)))
                            .build(),
                    ).build(),
                ZoneId.of("CET"),
            )

        assertThat(queryFilter.latestStatusTimestampMin)
            .isEqualTo(OffsetDateTime.of(2024, 1, 1, 14, 0, 0, 0, ZoneOffset.UTC))
        assertThat(queryFilter.latestStatusTimestampMax)
            .isEqualTo(OffsetDateTime.of(2024, 1, 2, 16, 0, 0, 0, ZoneOffset.UTC))
    }
}
