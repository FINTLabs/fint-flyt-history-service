package no.novari.flyt.instance

import no.novari.flyt.history.InstanceErrorEventService
import no.novari.flyt.history.InstanceInfoEventService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.instance.outbox.InstanceRegisteredOutboxRepository
import no.novari.flyt.instance.outbox.outboxEvent
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

class InstanceRegistrationServiceTest {
    @Test
    fun `missing persisted identity aborts registration before recording history`() {
        val instanceService: InstanceService = mock()
        val infoEventService: InstanceInfoEventService = mock()
        val offsetRepository: InstanceReceivedOffsetRepository = mock()
        val outboxRepository: InstanceRegisteredOutboxRepository = mock()
        val messageId =
            InstanceReceivedMessageId(
                "instance-received-test",
                0,
                0,
                "test-cluster",
                UUID.fromString("d254590e-616f-4e52-ac28-f84d55bb4281"),
            )
        val service =
            InstanceRegistrationService(
                instanceService,
                infoEventService,
                mock<InstanceErrorEventService>(),
                offsetRepository,
                outboxRepository,
                Clock.fixed(Instant.parse("2026-10-21T10:20:30Z"), ZoneOffset.UTC),
                "registration-test-app",
            )
        val dto = InstanceObjectDto()
        val headers =
            InstanceFlowHeaders
                .builder()
                .sourceApplicationId(1L)
                .sourceApplicationIntegrationId("integration-1")
                .sourceApplicationInstanceId("source-instance-1")
                .correlationId(UUID.fromString("53134ef2-4480-46d6-99a3-1920d36ea333"))
                .build()
        whenever(offsetRepository.lockForProcessing(messageId, 0)).thenReturn(0L)
        whenever(instanceService.save(dto)).thenReturn(InstanceObjectDto())

        assertThatThrownBy { service.register(messageId, 0, headers, dto) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Persisted instance must have an id")
        verifyNoInteractions(infoEventService, outboxRepository)
    }

    @Test
    fun `history and outbox share one millisecond registration timestamp`() {
        val event = outboxEvent()
        val instanceService: InstanceService = mock()
        val info: InstanceInfoEventService = mock()
        val offsets: InstanceReceivedOffsetRepository = mock()
        val outbox: InstanceRegisteredOutboxRepository = mock()
        val now = Instant.parse("2026-10-23T10:20:30.123456789Z")
        val service =
            InstanceRegistrationService(
                instanceService,
                info,
                mock<InstanceErrorEventService>(),
                offsets,
                outbox,
                Clock.fixed(now, ZoneOffset.UTC),
                event.applicationId,
            )
        whenever(offsets.lockForProcessing(event.messageId, 0)).thenReturn(0L)
        whenever(instanceService.save(event.instance)).thenReturn(event.instance)

        service.register(event.messageId, 0, event.headers, event.instance)

        val timestamp = now.truncatedTo(ChronoUnit.MILLIS)
        verify(info).registerEvent(event.headers, EventCategory.INSTANCE_REGISTERED, timestamp, event.applicationId)
        verify(outbox).enqueue(event.messageId, event.headers, event.instance, timestamp, event.applicationId)
    }
}
