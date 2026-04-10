package no.novari.flyt.history

import no.novari.flyt.history.mapping.InstanceFlowHeadersMappingService
import no.novari.flyt.history.repository.entities.InstanceFlowHeadersEmbeddable
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.UUID

class InstanceFlowHeadersMappingServiceTest {
    private val mapper = InstanceFlowHeadersMappingService()

    @Test
    fun testToEmbeddable() {
        val instanceFlowHeaders =
            InstanceFlowHeaders
                .builder()
                .sourceApplicationId(1L)
                .sourceApplicationIntegrationId("integrationId")
                .sourceApplicationInstanceId("instanceId")
                .correlationId(UUID.randomUUID())
                .integrationId(1L)
                .instanceId(1L)
                .configurationId(1L)
                .archiveInstanceId("archiveId")
                .build()

        val embeddable = mapper.toEmbeddable(instanceFlowHeaders)

        assertNotNull(embeddable)
        assertEquals(instanceFlowHeaders.sourceApplicationId, embeddable.sourceApplicationId)
        assertEquals(instanceFlowHeaders.sourceApplicationIntegrationId, embeddable.sourceApplicationIntegrationId)
        assertEquals(instanceFlowHeaders.sourceApplicationInstanceId, embeddable.sourceApplicationInstanceId)
        assertEquals(instanceFlowHeaders.correlationId, embeddable.correlationId)
        assertEquals(instanceFlowHeaders.integrationId, embeddable.integrationId)
        assertEquals(instanceFlowHeaders.instanceId, embeddable.instanceId)
        assertEquals(instanceFlowHeaders.configurationId, embeddable.configurationId)
        assertEquals(instanceFlowHeaders.archiveInstanceId, embeddable.archiveInstanceId)
    }

    @Test
    fun testToInstanceFlowHeaders() {
        val embeddable =
            InstanceFlowHeadersEmbeddable
                .builder()
                .sourceApplicationId(1L)
                .sourceApplicationIntegrationId("integrationId")
                .sourceApplicationInstanceId("instanceId")
                .fileIds(listOf(UUID.randomUUID()))
                .correlationId(UUID.randomUUID())
                .integrationId(1L)
                .instanceId(1L)
                .configurationId(1L)
                .archiveInstanceId("archiveId")
                .build()

        val instanceFlowHeaders = mapper.toInstanceFlowHeaders(embeddable)

        assertNotNull(instanceFlowHeaders)
        assertEquals(embeddable.sourceApplicationId, instanceFlowHeaders.sourceApplicationId)
        assertEquals(embeddable.sourceApplicationIntegrationId, instanceFlowHeaders.sourceApplicationIntegrationId)
        assertEquals(embeddable.sourceApplicationInstanceId, instanceFlowHeaders.sourceApplicationInstanceId)
        assertEquals(embeddable.correlationId, instanceFlowHeaders.correlationId)
        assertEquals(embeddable.fileIds, instanceFlowHeaders.fileIds)
        assertEquals(embeddable.integrationId, instanceFlowHeaders.integrationId)
        assertEquals(embeddable.instanceId, instanceFlowHeaders.instanceId)
        assertEquals(embeddable.configurationId, instanceFlowHeaders.configurationId)
        assertEquals(embeddable.archiveInstanceId, instanceFlowHeaders.archiveInstanceId)
    }
}
