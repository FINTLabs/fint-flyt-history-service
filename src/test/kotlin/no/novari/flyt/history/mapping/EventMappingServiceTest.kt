package no.novari.flyt.history.mapping

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
import java.time.OffsetDateTime
import java.time.ZoneOffset

class EventMappingServiceTest {
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
        assertThat(event).hasAllNullFieldsOrPropertiesExcept("errors")
        assertThat(event.errors).isEmpty()
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

        val event =
            eventMappingService.toEvent(
                EventEntity
                    .builder()
                    .id(1)
                    .instanceFlowHeaders(instanceFlowHeadersEmbeddable)
                    .name("testName")
                    .timestamp(offsetDateTime)
                    .type(EventType.INFO)
                    .applicationId("testApplicationId")
                    .errors(listOf(errorEntity1, errorEntity2))
                    .build(),
            )

        verify(instanceFlowHeadersMappingService, times(1)).toInstanceFlowHeaders(instanceFlowHeadersEmbeddable)
        verify(eventCategorizationService, times(1)).getCategoryByEventName("testName")
        verifyNoMoreInteractions(instanceFlowHeadersMappingService, eventCategorizationService)

        assertThat(event.instanceFlowHeaders).isSameAs(instanceFlowHeaders)
        assertThat(event.category).isEqualTo(EventCategory.INSTANCE_DISPATCHED)
        assertThat(event.timestamp).isEqualTo(offsetDateTime)
        assertThat(event.type).isEqualTo(EventType.INFO)
        assertThat(event.applicationId).isEqualTo("testApplicationId")
        assertThat(event.errors).containsExactly(errorEntity1, errorEntity2)
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
        assertThat(event).hasAllNullFieldsOrPropertiesExcept("errors")
        assertThat(event.errors).isEmpty()
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

        val eventPage: Page<Event> =
            eventMappingService.toEventPage(
                PageImpl(
                    listOf(
                        EventEntity
                            .builder()
                            .id(1)
                            .instanceFlowHeaders(instanceFlowHeadersEmbeddable)
                            .name("testName")
                            .timestamp(offsetDateTime)
                            .type(EventType.INFO)
                            .applicationId("testApplicationId")
                            .errors(listOf(errorEntity1, errorEntity2))
                            .build(),
                    ),
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
        assertThat(event.type).isEqualTo(EventType.INFO)
        assertThat(event.applicationId).isEqualTo("testApplicationId")
        assertThat(event.errors).containsExactly(errorEntity1, errorEntity2)
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
        assertThat(eventEntity).hasAllNullFieldsOrPropertiesExcept("id", "errors")
        assertThat(eventEntity.id).isZero()
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
        assertThat(eventEntity.type).isEqualTo(EventType.INFO)
        assertThat(eventEntity.applicationId).isEqualTo("testApplicationId")
        assertThat(eventEntity.errors).containsExactly(errorEntity1, errorEntity2)
    }
}
