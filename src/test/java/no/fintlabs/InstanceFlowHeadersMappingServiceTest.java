package no.fintlabs;

import no.fintlabs.flyt.kafka.headers.InstanceFlowHeaders;
import no.fintlabs.mapping.InstanceFlowHeadersMappingService;
import no.fintlabs.repository.entities.InstanceFlowHeadersEmbeddable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InstanceFlowHeadersMappingServiceTest {

    private final InstanceFlowHeadersMappingService mapper = new InstanceFlowHeadersMappingService();

    @Test
    void testToEmbeddable() {
        InstanceFlowHeaders instanceFlowHeaders = InstanceFlowHeaders.builder()
                .sourceApplicationId(1L)
                .sourceApplicationIntegrationId("integrationId")
                .sourceApplicationInstanceId("instanceId")
                .correlationId(UUID.randomUUID())
                .integrationId(1L)
                .instanceId(1L)
                .configurationId(1L)
                .archiveInstanceId("archiveId")
                .build();

        InstanceFlowHeadersEmbeddable embeddable = mapper.toEmbeddable(instanceFlowHeaders);

        assertNotNull(embeddable);
        assertEquals(instanceFlowHeaders.getSourceApplicationId(), embeddable.getSourceApplicationId());
        assertEquals(instanceFlowHeaders.getSourceApplicationIntegrationId(), embeddable.getSourceApplicationIntegrationId());
        assertEquals(instanceFlowHeaders.getSourceApplicationInstanceId(), embeddable.getSourceApplicationInstanceId());
        assertEquals(instanceFlowHeaders.getCorrelationId(), embeddable.getCorrelationId());
        assertEquals(instanceFlowHeaders.getIntegrationId(), embeddable.getIntegrationId());
        assertEquals(instanceFlowHeaders.getInstanceId(), embeddable.getInstanceId());
        assertEquals(instanceFlowHeaders.getConfigurationId(), embeddable.getConfigurationId());
        assertEquals(instanceFlowHeaders.getArchiveInstanceId(), embeddable.getArchiveInstanceId());
    }

    @Test
    void testToInstanceFlowHeaders() {
        InstanceFlowHeadersEmbeddable embeddable = InstanceFlowHeadersEmbeddable.builder()
                .sourceApplicationId(1L)
                .sourceApplicationIntegrationId("integrationId")
                .sourceApplicationInstanceId("instanceId")
                .fileIds(List.of(UUID.randomUUID()))
                .correlationId(UUID.randomUUID())
                .integrationId(1L)
                .instanceId(1L)
                .configurationId(1L)
                .archiveInstanceId("archiveId")
                .build();

        InstanceFlowHeaders instanceFlowHeaders = mapper.toInstanceFlowHeaders(embeddable);

        assertNotNull(instanceFlowHeaders);
        assertEquals(embeddable.getSourceApplicationId(), instanceFlowHeaders.getSourceApplicationId());
        assertEquals(embeddable.getSourceApplicationIntegrationId(), instanceFlowHeaders.getSourceApplicationIntegrationId());
        assertEquals(embeddable.getSourceApplicationInstanceId(), instanceFlowHeaders.getSourceApplicationInstanceId());
        assertEquals(embeddable.getCorrelationId(), instanceFlowHeaders.getCorrelationId());
        assertEquals(embeddable.getFileIds(), instanceFlowHeaders.getFileIds());
        assertEquals(embeddable.getIntegrationId(), instanceFlowHeaders.getIntegrationId());
        assertEquals(embeddable.getInstanceId(), instanceFlowHeaders.getInstanceId());
        assertEquals(embeddable.getConfigurationId(), instanceFlowHeaders.getConfigurationId());
        assertEquals(embeddable.getArchiveInstanceId(), instanceFlowHeaders.getArchiveInstanceId());
    }

}
