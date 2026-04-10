package no.novari.flyt.history.validation

import no.novari.flyt.history.model.time.ManualTimeFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class MinTimestampBeforeMaxTimestampValidatorTest {
    @Test
    fun `given timestamp min equal to timestamp max should return false`() {
        val valid =
            MinTimestampBeforeMaxTimestampValidator().isValid(
                ManualTimeFilter
                    .builder()
                    .min(OffsetDateTime.of(LocalDateTime.of(2025, 1, 1, 0, 0), ZoneOffset.UTC))
                    .max(OffsetDateTime.of(LocalDateTime.of(2025, 1, 1, 0, 0), ZoneOffset.UTC))
                    .build(),
                null,
            )

        assertThat(valid).isFalse()
    }

    @Test
    fun `given timestamp min after timestamp max should return false`() {
        val valid =
            MinTimestampBeforeMaxTimestampValidator().isValid(
                ManualTimeFilter
                    .builder()
                    .min(OffsetDateTime.of(LocalDateTime.of(2025, 1, 1, 1, 0), ZoneOffset.UTC))
                    .max(OffsetDateTime.of(LocalDateTime.of(2025, 1, 1, 0, 0), ZoneOffset.UTC))
                    .build(),
                null,
            )

        assertThat(valid).isFalse()
    }

    @Test
    fun `given timestamp min before timestamp max should return true`() {
        val valid =
            MinTimestampBeforeMaxTimestampValidator().isValid(
                ManualTimeFilter
                    .builder()
                    .min(OffsetDateTime.of(LocalDateTime.of(2025, 1, 1, 0, 0), ZoneOffset.UTC))
                    .max(OffsetDateTime.of(LocalDateTime.of(2025, 1, 1, 1, 0), ZoneOffset.UTC))
                    .build(),
                null,
            )

        assertThat(valid).isTrue()
    }

    @Test
    fun `given timestamp min and no timestamp max should return true`() {
        val valid =
            MinTimestampBeforeMaxTimestampValidator().isValid(
                ManualTimeFilter
                    .builder()
                    .min(OffsetDateTime.of(LocalDateTime.of(2025, 1, 1, 0, 0), ZoneOffset.UTC))
                    .build(),
                null,
            )

        assertThat(valid).isTrue()
    }

    @Test
    fun `given no timestamp min and timestamp max should return true`() {
        val valid =
            MinTimestampBeforeMaxTimestampValidator().isValid(
                ManualTimeFilter
                    .builder()
                    .max(OffsetDateTime.of(LocalDateTime.of(2025, 1, 1, 0, 0), ZoneOffset.UTC))
                    .build(),
                null,
            )

        assertThat(valid).isTrue()
    }

    @Test
    fun `given no timestamps should return true`() {
        val valid =
            MinTimestampBeforeMaxTimestampValidator().isValid(
                ManualTimeFilter
                    .builder()
                    .build(),
                null,
            )

        assertThat(valid).isTrue()
    }
}
