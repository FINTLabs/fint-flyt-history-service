package no.novari.flyt.instance

import no.novari.flyt.history.EventService
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.UUID

class InstanceCleanupServiceTest {
    private val instances: InstanceService = mock()
    private val events: EventService = mock()
    private val deletion: InstanceDeletionService = mock()
    private val cleanup = InstanceCleanupService(instances, events, deletion, 17)
    private val headers =
        InstanceFlowHeaders
            .builder()
            .instanceId(1L)
            .sourceApplicationId(1L)
            .sourceApplicationIntegrationId("integration")
            .sourceApplicationInstanceId("source-instance")
            .correlationId(UUID.randomUUID())
            .build()

    @AfterEach
    fun clearInterrupt() {
        Thread.interrupted()
    }

    @Test
    fun `missing registration headers do not prevent deletion or fabricate an event`() {
        whenever(instances.getAllOlderThan(17)).thenReturn(listOf(InstanceObjectDto(id = 1L)))

        cleanup.cleanUp()

        verify(instances).deleteById(1L)
        verifyNoInteractions(deletion)
    }

    @Test
    fun `a failed deletion does not block later candidates`() {
        whenever(
            instances.getAllOlderThan(17),
        ).thenReturn(listOf(InstanceObjectDto(id = 1L), InstanceObjectDto(id = 2L)))
        whenever(events.findInstanceFlowHeadersForLatestInstanceRegisteredEvent(1L)).thenReturn(headers)
        doThrow(IllegalStateException("delete failed")).whenever(deletion).delete(headers)

        cleanup.cleanUp()

        verify(deletion).delete(headers)
        verify(instances).deleteById(2L)
    }

    @Test
    fun `a failed header lookup preserves that instance and continues with the next`() {
        whenever(
            instances.getAllOlderThan(17),
        ).thenReturn(listOf(InstanceObjectDto(id = 1L), InstanceObjectDto(id = 2L)))
        whenever(events.findInstanceFlowHeadersForLatestInstanceRegisteredEvent(1L))
            .thenThrow(IllegalStateException("history unavailable"))

        cleanup.cleanUp()

        verify(instances, never()).deleteById(1L)
        verify(instances).deleteById(2L)
    }

    @Test
    fun `an interrupt stops cleanup before touching candidates`() {
        whenever(instances.getAllOlderThan(17)).thenReturn(listOf(InstanceObjectDto(id = 1L)))
        Thread.currentThread().interrupt()

        cleanup.cleanUp()

        verifyNoInteractions(events, deletion)
        assertThat(Thread.currentThread().isInterrupted).isTrue()
    }

    @Test
    fun `an interrupt during deletion stops cleanup and preserves the interrupt flag`() {
        whenever(
            instances.getAllOlderThan(17),
        ).thenReturn(listOf(InstanceObjectDto(id = 1L), InstanceObjectDto(id = 2L)))
        whenever(events.findInstanceFlowHeadersForLatestInstanceRegisteredEvent(1L)).thenReturn(headers)
        doAnswer { throw InterruptedException("interrupted") }
            .whenever(deletion)
            .delete(any())

        cleanup.cleanUp()

        verify(events, never()).findInstanceFlowHeadersForLatestInstanceRegisteredEvent(2L)
        assertThat(Thread.currentThread().isInterrupted).isTrue()
    }
}
