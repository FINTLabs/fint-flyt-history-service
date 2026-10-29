package no.novari.flyt.instance

import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.DataIntegrityViolationException
import java.util.UUID

class InstanceRegistrationHandlerTest {
    private val registrationService: InstanceRegistrationService = mock()
    private val handler = InstanceRegistrationHandler(registrationService)
    private val headers =
        InstanceFlowHeaders
            .builder()
            .sourceApplicationId(1L)
            .sourceApplicationIntegrationId("integration-1")
            .sourceApplicationInstanceId("source-instance-1")
            .correlationId(UUID.randomUUID())
            .build()
    private val instance = InstanceObjectDto()
    private val messageId = InstanceReceivedMessageId("instance-received-test", 0, 0, "test-cluster", UUID.randomUUID())

    @Test
    fun `technical and unknown failures propagate without rejection or business error history`() {
        val failures =
            listOf(
                InstanceReceivedOffsetException("generation mismatch"),
                IllegalArgumentException("unknown argument failure"),
                IllegalStateException("programming failure"),
                DataIntegrityViolationException("unknown constraint"),
                DataAccessResourceFailureException("database unavailable"),
            )
        failures.forEach { failure ->
            doThrow(failure).whenever(registrationService).register(messageId, 0, headers, instance)
            repeat(2) {
                assertThatThrownBy { handler.handle(messageId, 0, headers, instance) }.isSameAs(failure)
            }
        }
        verify(registrationService, never()).reject(any(), any(), any(), any())
    }

    @Test
    fun `known input failure returns rejected only after the rejection transaction succeeds`() {
        val failure = InstanceInputException(InstanceInputErrorCode.KEY_TOO_LONG, "$.valuePerKey[0].key")
        val outcome = InstanceRegistrationOutcome.Rejected(failure.code)
        doThrow(failure).whenever(registrationService).register(messageId, 0, headers, instance)
        whenever(registrationService.reject(messageId, 0, headers, failure)).thenReturn(outcome)

        assertThat(handler.handle(messageId, 0, headers, instance)).isSameAs(outcome)
    }

    @Test
    fun `failed rejection propagates and cannot be mistaken for success`() {
        val failure = InstanceInputException(InstanceInputErrorCode.KEY_TOO_LONG)
        val databaseFailure = DataAccessResourceFailureException("database unavailable")
        doThrow(failure).whenever(registrationService).register(messageId, 0, headers, instance)
        whenever(registrationService.reject(messageId, 0, headers, failure)).thenThrow(databaseFailure)

        assertThatThrownBy { handler.handle(messageId, 0, headers, instance) }.isSameAs(databaseFailure)
    }
}
