package no.novari.flyt.history.mapping

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorDisplayProperties
import no.novari.flyt.audit.actor.ActorDisplayResolver
import no.novari.flyt.history.model.event.Event
import no.novari.flyt.history.model.event.EventCategorizationService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.event.EventType
import no.novari.flyt.history.repository.entities.ErrorEntity
import no.novari.flyt.history.repository.entities.EventEntity
import no.novari.flyt.history.repository.entities.InstanceFlowHeadersEmbeddable
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.test.util.ReflectionTestUtils
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class EventMappingServiceTest {
    private val oid: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private lateinit var instanceFlowHeadersMappingService: InstanceFlowHeadersMappingService
    private lateinit var eventCategorizationService: EventCategorizationService
    private lateinit var eventMappingService: EventMappingService

    @BeforeEach
    fun setup() {
        instanceFlowHeadersMappingService = mock()
        eventCategorizationService = mock()
        eventMappingService =
            EventMappingService(
                instanceFlowHeadersMappingService,
                eventCategorizationService,
                ActorDisplayResolver(
                    { oids -> oids.associateWith { "Ola Nordmann" } },
                    ActorDisplayProperties(),
                ),
            )
    }

    @Test
    fun `given null event entity when to event then throw exception`() {
        assertThrows<IllegalArgumentException> {
            eventMappingService.toEvent(null)
        }
        verifyNoMoreInteractions(instanceFlowHeadersMappingService, eventCategorizationService)
    }

    @Test
    fun `given empty event entity when to event then return empty event`() {
        val event =
            eventMappingService.toEvent(
                EventEntity.builder().build(),
            )

        verifyNoMoreInteractions(instanceFlowHeadersMappingService, eventCategorizationService)
        assertThat(event).hasAllNullFieldsOrPropertiesExcept("isScrubbed", "errors")
        assertThat(event.isScrubbed).isFalse()
        assertThat(event.errors).isEmpty()
        assertThat(event.createdBy).isNull()
        assertThat(event.createdByActor).isNull()
    }

    @Test
    fun `given event entity with values when to event then return event with values`() {
        val instanceFlowHeadersEmbeddable: InstanceFlowHeadersEmbeddable = mock()
        val instanceFlowHeaders: InstanceFlowHeaders = mock()
        whenever(instanceFlowHeadersMappingService.toInstanceFlowHeaders(instanceFlowHeadersEmbeddable))
            .thenReturn(instanceFlowHeaders)
        whenever(eventCategorizationService.getCategoryByEventName("testName"))
            .thenReturn(EventCategory.INSTANCE_DISPATCHED)

        val offsetDateTime = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val errorEntity1: ErrorEntity = mock()
        val errorEntity2: ErrorEntity = mock()

        val createdAt = Instant.parse("2024-01-01T01:02:03Z")
        val createdBy = Actor.User(oid)

        val eventEntity =
            withAudit(
                EventEntity
                    .builder()
                    .id(1)
                    .instanceFlowHeaders(instanceFlowHeadersEmbeddable)
                    .name("testName")
                    .timestamp(offsetDateTime)
                    .isScrubbed(true)
                    .type(EventType.INFO)
                    .applicationId("testApplicationId")
                    .errors(listOf(errorEntity1, errorEntity2))
                    .build(),
                createdAt,
                createdBy,
            )

        val event =
            eventMappingService.toEvent(
                eventEntity,
            )

        verify(instanceFlowHeadersMappingService, times(1)).toInstanceFlowHeaders(instanceFlowHeadersEmbeddable)
        verify(eventCategorizationService, times(1)).getCategoryByEventName("testName")
        verifyNoMoreInteractions(instanceFlowHeadersMappingService, eventCategorizationService)

        assertThat(event.instanceFlowHeaders).isSameAs(instanceFlowHeaders)
        assertThat(event.category).isEqualTo(EventCategory.INSTANCE_DISPATCHED)
        assertThat(event.timestamp).isEqualTo(offsetDateTime)
        assertThat(event.isScrubbed).isTrue()
        assertThat(event.type).isEqualTo(EventType.INFO)
        assertThat(event.applicationId).isEqualTo("testApplicationId")
        assertThat(event.errors).containsExactly(errorEntity1, errorEntity2)
        assertThat(event.createdAt).isEqualTo(createdAt)
        assertThat(event.createdBy).isEqualTo("Ola Nordmann")
        assertThat(event.createdByActor).isEqualTo(createdBy)
    }

    @Test
    fun `given null event entity when to event page then throw exception`() {
        assertThrows<IllegalArgumentException> {
            eventMappingService.toEventPage(null)
        }
        verifyNoMoreInteractions(instanceFlowHeadersMappingService, eventCategorizationService)
    }

    @Test
    fun `given empty event entity when to page event then return empty event`() {
        val eventPage: Page<Event> =
            eventMappingService.toEventPage(
                PageImpl(
                    listOf(
                        EventEntity.builder().build(),
                    ),
                ),
            )

        verifyNoMoreInteractions(instanceFlowHeadersMappingService, eventCategorizationService)
        assertThat(eventPage).hasSize(1)
        val event = eventPage.content.first()
        assertThat(event).hasAllNullFieldsOrPropertiesExcept("isScrubbed", "errors")
        assertThat(event.isScrubbed).isFalse()
        assertThat(event.errors).isEmpty()
        assertThat(event.createdBy).isNull()
        assertThat(event.createdByActor).isNull()
    }

    @Test
    fun `given event entity with values when to event page then return event with values`() {
        val instanceFlowHeadersEmbeddable: InstanceFlowHeadersEmbeddable = mock()
        val instanceFlowHeaders: InstanceFlowHeaders = mock()
        whenever(instanceFlowHeadersMappingService.toInstanceFlowHeaders(instanceFlowHeadersEmbeddable))
            .thenReturn(instanceFlowHeaders)
        whenever(eventCategorizationService.getCategoryByEventName("testName"))
            .thenReturn(EventCategory.INSTANCE_DISPATCHED)

        val offsetDateTime = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val errorEntity1: ErrorEntity = mock()
        val errorEntity2: ErrorEntity = mock()

        val createdAt = Instant.parse("2024-01-01T01:02:03Z")
        val createdBy = Actor.User(oid)

        val eventEntity =
            withAudit(
                EventEntity
                    .builder()
                    .id(1)
                    .instanceFlowHeaders(instanceFlowHeadersEmbeddable)
                    .name("testName")
                    .timestamp(offsetDateTime)
                    .isScrubbed(true)
                    .type(EventType.INFO)
                    .applicationId("testApplicationId")
                    .errors(listOf(errorEntity1, errorEntity2))
                    .build(),
                createdAt,
                createdBy,
            )

        val eventPage: Page<Event> =
            eventMappingService.toEventPage(
                PageImpl(
                    listOf(eventEntity),
                ),
            )

        verify(instanceFlowHeadersMappingService, times(1)).toInstanceFlowHeaders(instanceFlowHeadersEmbeddable)
        verify(eventCategorizationService, times(1)).getCategoryByEventName("testName")
        verifyNoMoreInteractions(instanceFlowHeadersMappingService, eventCategorizationService)

        assertThat(eventPage).hasSize(1)
        val event = eventPage.content.first()
        assertThat(event.instanceFlowHeaders).isSameAs(instanceFlowHeaders)
        assertThat(event.category).isEqualTo(EventCategory.INSTANCE_DISPATCHED)
        assertThat(event.timestamp).isEqualTo(offsetDateTime)
        assertThat(event.isScrubbed).isTrue()
        assertThat(event.type).isEqualTo(EventType.INFO)
        assertThat(event.applicationId).isEqualTo("testApplicationId")
        assertThat(event.errors).containsExactly(errorEntity1, errorEntity2)
        assertThat(event.createdAt).isEqualTo(createdAt)
        assertThat(event.createdBy).isEqualTo("Ola Nordmann")
        assertThat(event.createdByActor).isEqualTo(createdBy)
    }

    @Test
    fun `given system and unknown actors when to event page then use resolver fallback display names`() {
        val createdAt = Instant.parse("2024-01-01T01:02:03Z")
        val systemEventEntity = withAudit(EventEntity.builder().build(), createdAt, Actor.System)
        val unknownEventEntity = withAudit(EventEntity.builder().build(), createdAt, Actor.Unknown)

        val eventPage: Page<Event> =
            eventMappingService.toEventPage(
                PageImpl(listOf(systemEventEntity, unknownEventEntity)),
            )

        assertThat(eventPage.content[0].createdBy).isEqualTo("System")
        assertThat(eventPage.content[0].createdByActor).isEqualTo(Actor.System)
        assertThat(eventPage.content[1].createdBy).isEqualTo("Ukjent")
        assertThat(eventPage.content[1].createdByActor).isEqualTo(Actor.Unknown)
    }

    @Test
    fun `given null event when to event entity then throw exception`() {
        assertThrows<IllegalArgumentException> {
            eventMappingService.toEventEntity(null)
        }
        verifyNoMoreInteractions(instanceFlowHeadersMappingService, eventCategorizationService)
    }

    @Test
    fun `given empty event when to event entity then return empty event entity`() {
        val eventEntity = eventMappingService.toEventEntity(Event.builder().build())

        verifyNoMoreInteractions(instanceFlowHeadersMappingService, eventCategorizationService)
        assertThat(eventEntity).hasAllNullFieldsOrPropertiesExcept("id", "isScrubbed", "errors")
        assertThat(eventEntity.id).isZero()
        assertThat(eventEntity.isScrubbed).isFalse()
        assertThat(eventEntity.errors).isEmpty()
    }

    @Test
    fun `given event with values when to event entity then return event with values`() {
        val instanceFlowHeaders: InstanceFlowHeaders = mock()
        val instanceFlowHeadersEmbeddable: InstanceFlowHeadersEmbeddable = mock()
        whenever(instanceFlowHeadersMappingService.toEmbeddable(instanceFlowHeaders))
            .thenReturn(instanceFlowHeadersEmbeddable)

        val offsetDateTime = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val errorEntity1: ErrorEntity = mock()
        val errorEntity2: ErrorEntity = mock()

        val eventEntity =
            eventMappingService.toEventEntity(
                Event
                    .builder()
                    .instanceFlowHeaders(instanceFlowHeaders)
                    .category(EventCategory.INSTANCE_DISPATCHED)
                    .timestamp(offsetDateTime)
                    .isScrubbed(true)
                    .type(EventType.INFO)
                    .applicationId("testApplicationId")
                    .errors(listOf(errorEntity1, errorEntity2))
                    .build(),
            )

        verify(instanceFlowHeadersMappingService, times(1)).toEmbeddable(instanceFlowHeaders)
        verifyNoMoreInteractions(instanceFlowHeadersMappingService, eventCategorizationService)

        assertThat(eventEntity.id).isZero()
        assertThat(eventEntity.instanceFlowHeaders).isSameAs(instanceFlowHeadersEmbeddable)
        assertThat(eventEntity.name).isEqualTo(EventCategory.INSTANCE_DISPATCHED.eventName)
        assertThat(eventEntity.timestamp).isEqualTo(offsetDateTime)
        assertThat(eventEntity.isScrubbed).isTrue()
        assertThat(eventEntity.type).isEqualTo(EventType.INFO)
        assertThat(eventEntity.applicationId).isEqualTo("testApplicationId")
        assertThat(eventEntity.errors).containsExactly(errorEntity1, errorEntity2)
    }

    @Test
    fun `given event when serializing then expose is scrubbed field`() {
        val json = jacksonObjectMapper().writeValueAsString(Event.builder().build())

        assertThat(json).contains("\"isScrubbed\":false")
        assertThat(json).doesNotContain("\"scrubbed\"")
    }

    private fun withAudit(
        eventEntity: EventEntity,
        createdAt: Instant,
        createdBy: Actor,
    ): EventEntity =
        eventEntity.apply {
            ReflectionTestUtils.setField(this, "createdAt", createdAt)
            ReflectionTestUtils.setField(this, "createdBy", createdBy)
        }
}
