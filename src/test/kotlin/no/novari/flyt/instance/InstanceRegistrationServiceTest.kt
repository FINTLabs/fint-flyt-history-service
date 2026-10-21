package no.novari.flyt.instance

import no.novari.flyt.history.InstanceInfoEventService
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class InstanceRegistrationServiceTest {
    @Test
    fun `missing persisted identity aborts registration before recording history`() {
        val instanceService: InstanceService = mock()
        val infoEventService: InstanceInfoEventService = mock()
        val service =
            InstanceRegistrationService(
                instanceService,
                infoEventService,
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
        whenever(instanceService.save(dto)).thenReturn(InstanceObjectDto())

        assertThatThrownBy { service.register(headers, dto) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Persisted instance must have an id")
        verifyNoInteractions(infoEventService)
    }
}
