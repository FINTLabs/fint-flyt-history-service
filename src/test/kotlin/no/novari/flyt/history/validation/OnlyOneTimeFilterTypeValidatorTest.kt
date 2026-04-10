package no.novari.flyt.history.validation

import no.novari.flyt.history.model.instance.ActiveTimePeriod
import no.novari.flyt.history.model.time.ManualTimeFilter
import no.novari.flyt.history.model.time.OffsetTimeFilter
import no.novari.flyt.history.model.time.TimeFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class OnlyOneTimeFilterTypeValidatorTest {
    @Test
    fun `given null time filter should return true`() {
        val valid =
            OnlyOneTimeFilterTypeValidator().isValid(
                null,
                null,
            )

        assertThat(valid).isTrue()
    }

    @Test
    fun `given all time filters should return false`() {
        val valid =
            OnlyOneTimeFilterTypeValidator().isValid(
                TimeFilter
                    .builder()
                    .offset(mock<OffsetTimeFilter>())
                    .currentPeriod(ActiveTimePeriod.TODAY)
                    .manual(mock<ManualTimeFilter>())
                    .build(),
                null,
            )

        assertThat(valid).isFalse()
    }

    @Test
    fun `given two time filters should return false`() {
        val valid =
            OnlyOneTimeFilterTypeValidator().isValid(
                TimeFilter
                    .builder()
                    .currentPeriod(ActiveTimePeriod.TODAY)
                    .manual(mock<ManualTimeFilter>())
                    .build(),
                null,
            )

        assertThat(valid).isFalse()
    }

    @Test
    fun `given no time filter should return true`() {
        val valid =
            OnlyOneTimeFilterTypeValidator().isValid(
                TimeFilter
                    .builder()
                    .build(),
                null,
            )

        assertThat(valid).isTrue()
    }

    @Test
    fun `given only offset time filter should return true`() {
        val valid =
            OnlyOneTimeFilterTypeValidator().isValid(
                TimeFilter
                    .builder()
                    .offset(mock<OffsetTimeFilter>())
                    .build(),
                null,
            )

        assertThat(valid).isTrue()
    }

    @Test
    fun `given only current period time filter should return true`() {
        val valid =
            OnlyOneTimeFilterTypeValidator().isValid(
                TimeFilter
                    .builder()
                    .currentPeriod(ActiveTimePeriod.TODAY)
                    .build(),
                null,
            )

        assertThat(valid).isTrue()
    }

    @Test
    fun `given only manual time filter should return true`() {
        val valid =
            OnlyOneTimeFilterTypeValidator().isValid(
                TimeFilter
                    .builder()
                    .manual(mock<ManualTimeFilter>())
                    .build(),
                null,
            )

        assertThat(valid).isTrue()
    }
}
