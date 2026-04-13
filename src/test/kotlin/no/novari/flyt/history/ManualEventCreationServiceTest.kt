package no.novari.flyt.history

import no.novari.flyt.history.exceptions.LatestStatusEventNotOfTypeErrorException
import no.novari.flyt.history.exceptions.NoPreviousStatusEventsFoundException
import no.novari.flyt.history.model.SourceApplicationAggregateInstanceId
import no.novari.flyt.history.model.event.Event
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.event.EventType
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class ManualEventCreationServiceTest {
    private val clock: Clock = Clock.fixed(Instant.ofEpochMilli(100000), ZoneOffset.UTC)
    private lateinit var uuidService: UuidService
    private lateinit var eventService: EventService
    private lateinit var manualEventCreationService: ManualEventCreationService

    @BeforeEach
    fun setup() {
        uuidService = mock()
        eventService = mock()
        manualEventCreationService =
            ManualEventCreationService(
                clock,
                uuidService,
                "testApplicationId",
                eventService,
            )
    }

    @Test
    fun `given no previous event for aggregate source application id when save then throw exception`() {
        val sourceApplicationAggregateInstanceId = testSourceApplicationAggregateInstanceId()
        whenever(
            eventService.findLatestStatusEventBySourceApplicationAggregateInstanceId(
                sourceApplicationAggregateInstanceId,
            ),
        ).thenReturn(null)

        assertThrows<NoPreviousStatusEventsFoundException> {
            manualEventCreationService.save(
                sourceApplicationAggregateInstanceId,
                EventCategory.INSTANCE_RECEIVED,
                "testArchiveInstanceId",
            )
        }

        verify(eventService, times(1))
            .findLatestStatusEventBySourceApplicationAggregateInstanceId(sourceApplicationAggregateInstanceId)
        verifyNoMoreInteractions(uuidService, eventService)
    }

    @Test
    fun `given previous event not of type error when save then throw exception`() {
        val sourceApplicationAggregateInstanceId = testSourceApplicationAggregateInstanceId()
        val event: Event = mock()
        whenever(event.type).thenReturn(EventType.INFO)
        whenever(
            eventService.findLatestStatusEventBySourceApplicationAggregateInstanceId(
                sourceApplicationAggregateInstanceId,
            ),
        ).thenReturn(event)

        assertThrows<LatestStatusEventNotOfTypeErrorException> {
            manualEventCreationService.save(
                sourceApplicationAggregateInstanceId,
                EventCategory.INSTANCE_RECEIVED,
                "testArchiveInstanceId",
            )
        }

        verify(eventService, times(1))
            .findLatestStatusEventBySourceApplicationAggregateInstanceId(sourceApplicationAggregateInstanceId)
        verifyNoMoreInteractions(uuidService, eventService)
    }

    @Test
    fun `given previous event is of type error when save then call event service save and return result`() {
        val sourceApplicationAggregateInstanceId = testSourceApplicationAggregateInstanceId()

        val latestEvent: Event = mock()
        whenever(latestEvent.type).thenReturn(EventType.ERROR)
        val latestEventInstanceFlowHeaders: InstanceFlowHeaders = mock()
        whenever(latestEventInstanceFlowHeaders.integrationId).thenReturn(10L)
        whenever(latestEvent.instanceFlowHeaders).thenReturn(latestEventInstanceFlowHeaders)
        whenever(
            eventService.findLatestStatusEventBySourceApplicationAggregateInstanceId(
                sourceApplicationAggregateInstanceId,
            ),
        ).thenReturn(latestEvent)

        val newEvent: Event = mock()
        val uuid = UUID.fromString("079c8300-8af7-4955-87bd-1efce87d52cd")
        whenever(uuidService.generateUuid()).thenReturn(uuid)
        whenever(
            eventService.save(
                argThat { eventMatches(this, uuid) },
            ),
        ).thenReturn(newEvent)

        val result =
            manualEventCreationService.save(
                sourceApplicationAggregateInstanceId,
                EventCategory.INSTANCE_MANUALLY_PROCESSED,
                "testArchiveInstanceId",
            )

        verify(eventService, times(1))
            .findLatestStatusEventBySourceApplicationAggregateInstanceId(sourceApplicationAggregateInstanceId)
        verify(uuidService, times(1)).generateUuid()
        verify(eventService, times(1)).save(argThat { eventMatches(this, uuid) })
        verifyNoMoreInteractions(uuidService, eventService)
        assertThat(result).isEqualTo(newEvent)
    }

    private fun testSourceApplicationAggregateInstanceId(): SourceApplicationAggregateInstanceId {
        return object : SourceApplicationAggregateInstanceId {
            override val sourceApplicationId = 1L

            override val sourceApplicationIntegrationId = "testSourceApplicationIntegrationId"

            override val sourceApplicationInstanceId = "testSourceApplicationInstanceId"
        }
    }

    private fun eventMatches(
        event: Event,
        uuid: UUID,
    ): Boolean {
        return event.instanceFlowHeaders ==
            InstanceFlowHeaders
                .builder()
                .sourceApplicationId(1L)
                .sourceApplicationIntegrationId("testSourceApplicationIntegrationId")
                .sourceApplicationInstanceId("testSourceApplicationInstanceId")
                .integrationId(10L)
                .correlationId(uuid)
                .archiveInstanceId("testArchiveInstanceId")
                .build() &&
            event.category == EventCategory.INSTANCE_MANUALLY_PROCESSED &&
            event.timestamp!!.isEqual(OffsetDateTime.now(clock)) &&
            event.type == EventType.INFO &&
            event.applicationId == "testApplicationId"
    }
}
