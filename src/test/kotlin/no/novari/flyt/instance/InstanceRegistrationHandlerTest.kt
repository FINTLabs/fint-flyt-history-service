package no.novari.flyt.instance

import no.novari.flyt.history.InstanceErrorEventService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class InstanceRegistrationHandlerTest {
    private val registrationService: InstanceRegistrationService = mock()
    private val errorEventService: InstanceErrorEventService = mock()
    private val timestamp = Instant.parse("2026-10-22T10:20:30Z")
    private val service =
        InstanceRegistrationHandler(
            registrationService,
            errorEventService,
            Clock.fixed(timestamp, ZoneOffset.UTC),
            "registration-test-app",
        )
    private val headers =
        InstanceFlowHeaders
            .builder()
            .sourceApplicationId(1L)
            .sourceApplicationIntegrationId("integration-1")
            .sourceApplicationInstanceId("source-instance-1")
            .correlationId(UUID.fromString("53134ef2-4480-46d6-99a3-1920d36ea333"))
            .build()
    private val instance = InstanceObjectDto()
    private val messageId = InstanceReceivedMessageId("instance-received-test", 0, 0)

    @Test
    fun `successful error recording still rethrows the original registration exception`() {
        val failure = IllegalStateException("registration failed")
        whenever(registrationService.register(messageId, headers, instance)).thenThrow(failure)

        assertThatThrownBy { service.handle(messageId, headers, instance) }.isSameAs(failure)

        verify(errorEventService).registerError(
            eq(headers),
            eq(EventCategory.INSTANCE_REGISTRATION_ERROR),
            any(),
            eq(timestamp),
            eq("registration-test-app"),
        )
    }

    @Test
    fun `error recording failure is suppressed on the original registration exception`() {
        val failure = IllegalStateException("registration failed")
        val errorFailure = IllegalArgumentException("error recording failed")
        whenever(registrationService.register(messageId, headers, instance)).thenThrow(failure)
        doThrow(errorFailure).whenever(errorEventService).registerError(
            any(),
            any<EventCategory>(),
            any(),
            any(),
            any(),
        )

        assertThatThrownBy { service.handle(messageId, headers, instance) }.isSameAs(failure)
        assertThat(failure.suppressed).containsExactly(errorFailure)
    }
}
