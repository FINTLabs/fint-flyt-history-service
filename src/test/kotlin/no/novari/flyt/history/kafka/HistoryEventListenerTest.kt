package no.novari.flyt.history.kafka

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorHeader
import no.novari.flyt.history.InstanceErrorEventService
import no.novari.flyt.history.InstanceInfoEventService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.instance.InstanceDeletionService
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import no.novari.flyt.kafka.model.Error
import no.novari.flyt.kafka.model.ErrorCollection
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.kafka.listener.ConsumerSeekAware
import java.time.Instant
import java.util.UUID

class HistoryEventListenerTest {
    private val topics: HistoryEventTopics =
        mock {
            on { category(RETRY) } doReturn EventCategory.INSTANCE_REQUESTED_FOR_RETRY
            on { category(MAPPED) } doReturn EventCategory.INSTANCE_MAPPED
            on { category(DISPATCHED) } doReturn EventCategory.INSTANCE_DISPATCHED
            on { category(MAPPING_ERROR) } doReturn EventCategory.INSTANCE_MAPPING_ERROR
        }
    private val infoEvents: InstanceInfoEventService = mock()
    private val errorEvents: InstanceErrorEventService = mock()
    private val deletion: InstanceDeletionService = mock()
    private val listener = HistoryEventListener(topics, infoEvents, errorEvents, deletion)
    private val headers = InstanceFlowHeaders(sourceApplicationId = 1L, correlationId = UUID.randomUUID())
    private val user = Actor.User(UUID.randomUUID())

    @Test
    fun `instance requested for retry registers the actor from the flyt actor header`() {
        listener.onInfoEvent(headers, RETRY, TIMESTAMP, ORIGIN, ActorHeader.toHeaderValue(user))

        verify(infoEvents).registerEvent(headers, EventCategory.INSTANCE_REQUESTED_FOR_RETRY, INSTANT, APP, user)
    }

    @Test
    fun `instance requested for retry without the flyt actor header registers no actor`() {
        listener.onInfoEvent(headers, RETRY, TIMESTAMP, ORIGIN, null)

        verify(infoEvents).registerEvent(headers, EventCategory.INSTANCE_REQUESTED_FOR_RETRY, INSTANT, APP, null)
    }

    @Test
    fun `other info events ignore the flyt actor header`() {
        listener.onInfoEvent(headers, MAPPED, TIMESTAMP, ORIGIN, ActorHeader.toHeaderValue(user))

        verify(infoEvents).registerEvent(headers, EventCategory.INSTANCE_MAPPED, INSTANT, APP, null)
        verifyNoInteractions(deletion)
    }

    @Test
    fun `instance dispatched registers the event and then deletes the instance`() {
        listener.onInfoEvent(headers, DISPATCHED, TIMESTAMP, ORIGIN, null)

        inOrder(infoEvents, deletion) {
            verify(infoEvents).registerEvent(headers, EventCategory.INSTANCE_DISPATCHED, INSTANT, APP, null)
            verify(deletion).delete(headers)
        }
    }

    @Test
    fun `legacy error events are registered with the category of their topic`() {
        val errors = ErrorCollection(Error(errorCode = "code"))

        LegacyErrorEventListener(topics, errorEvents).onErrorEvent(errors, headers, MAPPING_ERROR, TIMESTAMP, ORIGIN)

        verify(errorEvents).registerError(headers, EventCategory.INSTANCE_MAPPING_ERROR, errors, INSTANT, APP)
    }

    @Test
    fun `the instance-deleted scrubber does nothing and reads from the beginning`() {
        val scrubber = InstanceDeletedScrubberListener()
        scrubber.on(headers)
        val callback = mock<ConsumerSeekAware.ConsumerSeekCallback>()
        val assignments = mapOf(TopicPartition("org.flyt.event.instance-deleted", 0) to 42L)

        scrubber.onPartitionsAssigned(assignments, callback)

        verify(callback).seekToBeginning(assignments.keys)
    }

    private companion object {
        const val RETRY = "org.flyt.event.instance-requested-for-retry"
        const val MAPPED = "org.flyt.event.instance-mapped"
        const val DISPATCHED = "org.flyt.event.instance-dispatched"
        const val MAPPING_ERROR = "org.flyt.event.error.instance-mapping-error"
        const val TIMESTAMP = 1_700_000_000_000L
        const val APP = "fint-flyt-mapping-service"
        val INSTANT: Instant = Instant.ofEpochMilli(TIMESTAMP)
        val ORIGIN = APP.toByteArray()
    }
}
