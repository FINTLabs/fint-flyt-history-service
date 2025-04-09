package no.fintlabs.mapping;

import no.fintlabs.flyt.kafka.headers.InstanceFlowHeaders;
import no.fintlabs.model.event.Event;
import no.fintlabs.model.event.EventCategorizationService;
import no.fintlabs.model.event.EventCategory;
import no.fintlabs.model.event.EventType;
import no.fintlabs.repository.entities.ErrorEntity;
import no.fintlabs.repository.entities.EventEntity;
import no.fintlabs.repository.entities.InstanceFlowHeadersEmbeddable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class EventMappingServiceTest {

    InstanceFlowHeadersMappingService instanceFlowHeadersMappingService;
    EventCategorizationService eventCategorizationService;
    EventMappingService eventMappingService;

    @BeforeEach
    public void setup() {
        instanceFlowHeadersMappingService = mock(InstanceFlowHeadersMappingService.class);
        eventCategorizationService = mock(EventCategorizationService.class);
        eventMappingService = new EventMappingService(
                instanceFlowHeadersMappingService,
                eventCategorizationService
        );
    }

    @Test
    public void givenNullEventEntity_whenToEvent_thenThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> eventMappingService.toEvent(null)
        );
        verifyNoMoreInteractions(instanceFlowHeadersMappingService, eventCategorizationService);
    }

    @Test
    public void givenEmptyEventEntity_whenToEvent_thenReturnEmptyEvent() {
        Event event = eventMappingService.toEvent(
                EventEntity
                        .builder()
                        .build()
        );
        verifyNoMoreInteractions(instanceFlowHeadersMappingService, eventCategorizationService);
        assertThat(event).hasAllNullFieldsOrPropertiesExcept("errors");
        assertThat(event.getErrors()).isEmpty();
    }

    @Test
    public void givenEventEntityWithValues_whenToEvent_thenReturnEventWithValues() {
        InstanceFlowHeadersEmbeddable instanceFlowHeadersEmbeddable = mock(InstanceFlowHeadersEmbeddable.class);
        InstanceFlowHeaders instanceFlowHeaders = mock(InstanceFlowHeaders.class);
        when(instanceFlowHeadersMappingService.toInstanceFlowHeaders(instanceFlowHeadersEmbeddable))
                .thenReturn(instanceFlowHeaders);

        when(eventCategorizationService.getCategoryByEventName("testName"))
                .thenReturn(EventCategory.INSTANCE_DISPATCHED);

        OffsetDateTime offsetDateTime = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        ErrorEntity errorEntity1 = mock(ErrorEntity.class);
        ErrorEntity errorEntity2 = mock(ErrorEntity.class);


        Event event = eventMappingService.toEvent(
                EventEntity
                        .builder()
                        .id(1)
                        .instanceFlowHeaders(instanceFlowHeadersEmbeddable)
                        .name("testName")
                        .timestamp(offsetDateTime)
                        .type(EventType.INFO)
                        .applicationId("testApplicationId")
                        .errors(List.of(errorEntity1, errorEntity2))
                        .build()
        );

        verify(instanceFlowHeadersMappingService, times(1)).toInstanceFlowHeaders(
                instanceFlowHeadersEmbeddable
        );

        verify(eventCategorizationService, times(1)).getCategoryByEventName("testName");

        verifyNoMoreInteractions(instanceFlowHeadersMappingService, eventCategorizationService);

        assertThat(event.getInstanceFlowHeaders()).isSameAs(instanceFlowHeaders);
        assertThat(event.getCategory()).isEqualTo(EventCategory.INSTANCE_DISPATCHED);
        assertThat(event.getTimestamp()).isEqualTo(offsetDateTime);
        assertThat(event.getType()).isEqualTo(EventType.INFO);
        assertThat(event.getApplicationId()).isEqualTo("testApplicationId");
        assertThat(event.getErrors()).containsExactly(errorEntity1, errorEntity2);
    }

    @Test
    public void givenNullEventEntity_whenToEventPage_thenThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> eventMappingService.toEventPage(null)
        );
        verifyNoMoreInteractions(instanceFlowHeadersMappingService, eventCategorizationService);
    }

    @Test
    public void givenEmptyEventEntity_whenToPageEvent_thenReturnEmptyEvent() {
        Page<Event> eventPage = eventMappingService.toEventPage(
                new PageImpl<>(List.of(
                        EventEntity
                                .builder()
                                .build()
                ))
        );
        verifyNoMoreInteractions(instanceFlowHeadersMappingService, eventCategorizationService);
        assertThat(eventPage).hasSize(1);
        Event event = eventPage.getContent().get(0);
        assertThat(event).hasAllNullFieldsOrPropertiesExcept("errors");
        assertThat(event.getErrors()).isEmpty();
    }

    @Test
    public void givenEventEntityWithValues_whenToEventPage_thenReturnEventWithValues() {
        InstanceFlowHeadersEmbeddable instanceFlowHeadersEmbeddable = mock(InstanceFlowHeadersEmbeddable.class);
        InstanceFlowHeaders instanceFlowHeaders = mock(InstanceFlowHeaders.class);
        when(instanceFlowHeadersMappingService.toInstanceFlowHeaders(instanceFlowHeadersEmbeddable))
                .thenReturn(instanceFlowHeaders);

        when(eventCategorizationService.getCategoryByEventName("testName"))
                .thenReturn(EventCategory.INSTANCE_DISPATCHED);

        OffsetDateTime offsetDateTime = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        ErrorEntity errorEntity1 = mock(ErrorEntity.class);
        ErrorEntity errorEntity2 = mock(ErrorEntity.class);


        Page<Event> eventPage = eventMappingService.toEventPage(
                new PageImpl<>(List.of(
                        EventEntity
                                .builder()
                                .id(1)
                                .instanceFlowHeaders(instanceFlowHeadersEmbeddable)
                                .name("testName")
                                .timestamp(offsetDateTime)
                                .type(EventType.INFO)
                                .applicationId("testApplicationId")
                                .errors(List.of(errorEntity1, errorEntity2))
                                .build()
                ))
        );

        verify(instanceFlowHeadersMappingService, times(1)).toInstanceFlowHeaders(
                instanceFlowHeadersEmbeddable
        );

        verify(eventCategorizationService, times(1)).getCategoryByEventName("testName");

        verifyNoMoreInteractions(instanceFlowHeadersMappingService, eventCategorizationService);

        assertThat(eventPage).hasSize(1);
        Event event = eventPage.getContent().get(0);
        assertThat(event.getInstanceFlowHeaders()).isSameAs(instanceFlowHeaders);
        assertThat(event.getCategory()).isEqualTo(EventCategory.INSTANCE_DISPATCHED);
        assertThat(event.getTimestamp()).isEqualTo(offsetDateTime);
        assertThat(event.getType()).isEqualTo(EventType.INFO);
        assertThat(event.getApplicationId()).isEqualTo("testApplicationId");
        assertThat(event.getErrors()).containsExactly(errorEntity1, errorEntity2);
    }

    @Test
    public void givenNullEvent_whenToEventEntity_thenThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> eventMappingService.toEventEntity(null)
        );
        verifyNoMoreInteractions(instanceFlowHeadersMappingService, eventCategorizationService);
    }

    @Test
    public void givenEmptyEvent_whenToEventEntity_thenReturnEmptyEventEntity() {
        EventEntity eventEntity = eventMappingService.toEventEntity(Event.builder().build());
        verifyNoMoreInteractions(instanceFlowHeadersMappingService, eventCategorizationService);
        assertThat(eventEntity).hasAllNullFieldsOrPropertiesExcept("id", "errors");
        assertThat(eventEntity.getId()).isZero();
        assertThat(eventEntity.getErrors()).isEmpty();
    }

    @Test
    public void givenEventWithValues_whenToEventEntity_thenReturnEventWithValues() {
        InstanceFlowHeaders instanceFlowHeaders = mock(InstanceFlowHeaders.class);
        InstanceFlowHeadersEmbeddable instanceFlowHeadersEmbeddable = mock(InstanceFlowHeadersEmbeddable.class);
        when(instanceFlowHeadersMappingService.toEmbeddable(instanceFlowHeaders))
                .thenReturn(instanceFlowHeadersEmbeddable);

        OffsetDateTime offsetDateTime = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        ErrorEntity errorEntity1 = mock(ErrorEntity.class);
        ErrorEntity errorEntity2 = mock(ErrorEntity.class);

        EventEntity eventEntity = eventMappingService.toEventEntity(
                Event
                        .builder()
                        .instanceFlowHeaders(instanceFlowHeaders)
                        .category(EventCategory.INSTANCE_DISPATCHED)
                        .timestamp(offsetDateTime)
                        .type(EventType.INFO)
                        .applicationId("testApplicationId")
                        .errors(List.of(errorEntity1, errorEntity2))
                        .build()
        );

        verify(instanceFlowHeadersMappingService, times(1)).toEmbeddable(instanceFlowHeaders);

        verifyNoMoreInteractions(instanceFlowHeadersMappingService, eventCategorizationService);

        assertThat(eventEntity.getId()).isZero();
        assertThat(eventEntity.getInstanceFlowHeaders()).isSameAs(instanceFlowHeadersEmbeddable);
        assertThat(eventEntity.getName()).isEqualTo(EventCategory.INSTANCE_DISPATCHED.getEventName());
        assertThat(eventEntity.getTimestamp()).isEqualTo(offsetDateTime);
        assertThat(eventEntity.getType()).isEqualTo(EventType.INFO);
        assertThat(eventEntity.getApplicationId()).isEqualTo("testApplicationId");
        assertThat(eventEntity.getErrors()).containsExactly(errorEntity1, errorEntity2);
    }

}