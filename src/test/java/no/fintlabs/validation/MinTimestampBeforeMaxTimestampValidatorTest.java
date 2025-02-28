package no.fintlabs.validation;

import no.fintlabs.model.time.ManualTimeFilter;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class MinTimestampBeforeMaxTimestampValidatorTest {

    @Test
    public void givenTimestampMinEqualToTimestampMax_shouldReturnFalse() {
        boolean valid = new MinTimestampBeforeMaxTimestampValidator().isValid(
                ManualTimeFilter
                        .builder()
                        .min(OffsetDateTime.of(LocalDateTime.of(2025, 1, 1, 0, 0), ZoneOffset.UTC))
                        .max(OffsetDateTime.of(LocalDateTime.of(2025, 1, 1, 0, 0), ZoneOffset.UTC))
                        .build(),
                null
        );
        assertThat(valid).isFalse();
    }

    @Test
    public void givenTimestampMinAfterTimestampMax_shouldReturnFalse() {
        boolean valid = new MinTimestampBeforeMaxTimestampValidator().isValid(
                ManualTimeFilter
                        .builder()
                        .min(OffsetDateTime.of(LocalDateTime.of(2025, 1, 1, 1, 0), ZoneOffset.UTC))
                        .max(OffsetDateTime.of(LocalDateTime.of(2025, 1, 1, 0, 0), ZoneOffset.UTC))
                        .build(),
                null
        );
        assertThat(valid).isFalse();
    }

    @Test
    public void givenTimestampMinBeforeTimestampMax_shouldReturnTrue() {
        boolean valid = new MinTimestampBeforeMaxTimestampValidator().isValid(
                ManualTimeFilter
                        .builder()
                        .min(OffsetDateTime.of(LocalDateTime.of(2025, 1, 1, 0, 0), ZoneOffset.UTC))
                        .max(OffsetDateTime.of(LocalDateTime.of(2025, 1, 1, 1, 0), ZoneOffset.UTC))
                        .build(),
                null
        );
        assertThat(valid).isTrue();
    }

    @Test
    public void givenTimestampMinAndNoTimestampMax_shouldReturnTrue() {
        boolean valid = new MinTimestampBeforeMaxTimestampValidator().isValid(
                ManualTimeFilter
                        .builder()
                        .min(OffsetDateTime.of(LocalDateTime.of(2025, 1, 1, 0, 0), ZoneOffset.UTC))
                        .build(),
                null
        );
        assertThat(valid).isTrue();
    }

    @Test
    public void givenNoTimestampMinAndTimestampMax_shouldReturnTrue() {
        boolean valid = new MinTimestampBeforeMaxTimestampValidator().isValid(
                ManualTimeFilter
                        .builder()
                        .max(OffsetDateTime.of(LocalDateTime.of(2025, 1, 1, 0, 0), ZoneOffset.UTC))
                        .build(),
                null
        );
        assertThat(valid).isTrue();
    }

    @Test
    public void givenNoTimestamps_shouldReturnTrue() {
        boolean valid = new MinTimestampBeforeMaxTimestampValidator().isValid(
                ManualTimeFilter
                        .builder()
                        .build(),
                null
        );
        assertThat(valid).isTrue();
    }

}