package no.novari.flyt.history

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorContext
import no.novari.flyt.history.mapping.InstanceFlowHeadersMappingService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.event.EventType
import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.history.repository.entities.EventEntity
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class InstanceInfoEventServiceTest {
    private val eventRepository: EventRepository = mock()
    private val service = InstanceInfoEventService(eventRepository, InstanceFlowHeadersMappingService())
    private val timestamp = Instant.parse("2026-01-15T10:20:30Z")

    @Test
    fun `maps and saves info event without setting actor context`() {
        val headers = instanceFlowHeaders()
        var actorDuringSave: Actor? = null
        whenever(eventRepository.save(any<EventEntity>())).thenAnswer { invocation ->
            actorDuringSave = ActorContext.currentActor()
            invocation.getArgument(0)
        }

        service.registerEvent(headers, EventCategory.INSTANCE_REGISTERED, timestamp, "source-app")

        val saved = savedEvent()
        assertThat(saved.instanceFlowHeaders?.sourceApplicationId).isEqualTo(headers.sourceApplicationId)
        assertThat(saved.instanceFlowHeaders?.sourceApplicationIntegrationId)
            .isEqualTo(headers.sourceApplicationIntegrationId)
        assertThat(saved.instanceFlowHeaders?.sourceApplicationInstanceId)
            .isEqualTo(headers.sourceApplicationInstanceId)
        assertThat(saved.instanceFlowHeaders?.correlationId).isEqualTo(headers.correlationId)
        assertThat(saved.name).isEqualTo(EventCategory.INSTANCE_REGISTERED.eventName)
        assertThat(saved.type).isEqualTo(EventType.INFO)
        assertThat(saved.timestamp).isEqualTo(timestamp.atOffset(ZoneOffset.UTC))
        assertThat(saved.applicationId).isEqualTo("source-app")
        assertThat(actorDuringSave).isNull()
        assertThat(ActorContext.currentActor()).isNull()
    }

    @Test
    fun `uses explicit actor and restores previous actor context`() {
        val actor = Actor.User(UUID.fromString("53134ef2-4480-46d6-99a3-1920d36ea333"))
        var actorDuringSave: Actor? = null
        whenever(eventRepository.save(any<EventEntity>())).thenAnswer { invocation ->
            actorDuringSave = ActorContext.currentActor()
            invocation.getArgument(0)
        }

        ActorContext.withActor(Actor.System) {
            service.registerEvent(
                instanceFlowHeaders(),
                EventCategory.INSTANCE_REQUESTED_FOR_RETRY,
                timestamp,
                "source-app",
                actor,
            )
            assertThat(ActorContext.currentActor()).isEqualTo(Actor.System)
        }

        assertThat(actorDuringSave).isEqualTo(actor)
        assertThat(ActorContext.currentActor()).isNull()
    }

    @Test
    fun `restores actor context when saving fails`() {
        val actor = Actor.User(UUID.fromString("53134ef2-4480-46d6-99a3-1920d36ea333"))
        whenever(eventRepository.save(any<EventEntity>())).thenThrow(IllegalStateException("save failed"))

        assertThatThrownBy {
            service.registerEvent(
                instanceFlowHeaders(),
                EventCategory.INSTANCE_REQUESTED_FOR_RETRY,
                timestamp,
                "source-app",
                actor,
            )
        }.isInstanceOf(IllegalStateException::class.java)

        assertThat(ActorContext.currentActor()).isNull()
    }

    @Test
    fun `rejects error category without saving`() {
        assertThatThrownBy {
            service.registerEvent(instanceFlowHeaders(), EventCategory.INSTANCE_RECEIVAL_ERROR, timestamp, "source-app")
        }.isInstanceOf(IllegalArgumentException::class.java)

        verifyNoInteractions(eventRepository)
    }

    private fun savedEvent(): EventEntity {
        val eventCaptor = argumentCaptor<EventEntity>()
        verify(eventRepository).save(eventCaptor.capture())
        return eventCaptor.firstValue
    }

    private fun instanceFlowHeaders(): InstanceFlowHeaders =
        InstanceFlowHeaders
            .builder()
            .sourceApplicationId(1L)
            .sourceApplicationIntegrationId("sa-integration-1")
            .sourceApplicationInstanceId("sa-instance-1")
            .correlationId(UUID.fromString("2ee6f95e-44c3-11ed-b878-0242ac120002"))
            .integrationId(100L)
            .build()
}
