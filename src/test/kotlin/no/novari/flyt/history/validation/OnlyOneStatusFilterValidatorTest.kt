package no.novari.flyt.history.validation

import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.instance.InstanceFlowSummariesFilter
import no.novari.flyt.history.model.instance.InstanceStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OnlyOneStatusFilterValidatorTest {
    @Test
    fun `given null filter then return true`() {
        val valid = OnlyOneStatusFilterValidator().isValid(null, null)

        assertThat(valid).isTrue()
    }

    @Test
    fun `given filter has no statuses or latest status events then return true`() {
        val valid =
            OnlyOneStatusFilterValidator().isValid(
                InstanceFlowSummariesFilter.builder().build(),
                null,
            )

        assertThat(valid).isTrue()
    }

    @Test
    fun `given filter has statuses and no latest status events then return true`() {
        val valid =
            OnlyOneStatusFilterValidator().isValid(
                InstanceFlowSummariesFilter
                    .builder()
                    .statuses(listOf(InstanceStatus.IN_PROGRESS, InstanceStatus.ABORTED))
                    .build(),
                null,
            )

        assertThat(valid).isTrue()
    }

    @Test
    fun `given filter has latest status events and no statuses then return true`() {
        val valid =
            OnlyOneStatusFilterValidator().isValid(
                InstanceFlowSummariesFilter
                    .builder()
                    .latestStatusEvents(
                        listOf(
                            EventCategory.INSTANCE_MAPPED,
                            EventCategory.INSTANCE_RECEIVAL_ERROR,
                        ),
                    ).build(),
                null,
            )

        assertThat(valid).isTrue()
    }

    @Test
    fun `given filter has statuses and latest status events then return false`() {
        val valid =
            OnlyOneStatusFilterValidator().isValid(
                InstanceFlowSummariesFilter
                    .builder()
                    .statuses(listOf(InstanceStatus.IN_PROGRESS, InstanceStatus.ABORTED))
                    .latestStatusEvents(
                        listOf(
                            EventCategory.INSTANCE_MAPPED,
                            EventCategory.INSTANCE_RECEIVAL_ERROR,
                        ),
                    ).build(),
                null,
            )

        assertThat(valid).isFalse()
    }
}
