package no.fintlabs;

import lombok.AllArgsConstructor;
import no.fintlabs.model.Event;
import no.fintlabs.model.EventDto;
import no.fintlabs.model.ManualEventDto;
import no.fintlabs.repositories.EventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static no.fintlabs.EventNames.INSTANCE_DELETED;

@Service
@AllArgsConstructor
public class EventService {

    private final EventRepository eventRepository;

    public void save(Event event) {
        eventRepository.save(event);
    }

    public Page<EventDto> findAll(Pageable pageable) {
        return convertPageOfEventIntoPageOfEventDto(eventRepository.findAll(pageable));
    }

    public Page<EventDto> getMergedLatestEvents(Pageable pageable) {
        List<Event> latestEvents = eventRepository
                .findLatestEventPerSourceApplicationInstanceId(pageable).getContent();

        List<Event> latestNonDeletedEvents = eventRepository
                .findLatestEventNotDeletedPerSourceApplicationInstanceId(pageable).getContent();

        return getEventDtos(pageable, latestNonDeletedEvents, latestEvents);
    }

    public Page<EventDto> getMergedLatestEventsWhereSourceApplicationIdIn(
            List<Long> sourceApplicationIds,
            Pageable pageable
    ) {
        List<Event> latestEvents = eventRepository
                .findLatestEventPerSourceApplicationInstanceIdAndSourceApplicationIdIn(
                        sourceApplicationIds,
                        pageable
                ).getContent();

        List<Event> latestNonDeletedEvents = eventRepository
                .findLatestEventNotDeletedPerSourceApplicationInstanceIdAndSourceApplicationIdIn(
                        sourceApplicationIds,
                        pageable
                ).getContent();

        return getEventDtos(pageable, latestNonDeletedEvents, latestEvents);
    }

    private PageImpl<EventDto> getEventDtos(Pageable pageable, List<Event> latestNonDeletedEvents, List<Event> latestEvents) {
        Map<String, Event> nonDeletedEventMap = latestNonDeletedEvents.stream()
                .collect(
                        Collectors.toMap(
                                event -> event.getInstanceFlowHeaders().getSourceApplicationInstanceId(),
                                event -> event
                        )
                );

        List<EventDto> mergedEvents = new ArrayList<>();

        for (Event latestEvent : latestEvents) {
            if (INSTANCE_DELETED.equals(latestEvent.getName())) {
                Event nonDeletedEvent = nonDeletedEventMap
                        .get(latestEvent.getInstanceFlowHeaders().getSourceApplicationInstanceId());
                if (nonDeletedEvent != null) {
                    EventDto updatedEventDto = EventToEventDto(nonDeletedEvent);
                    updatedEventDto.setStatus(INSTANCE_DELETED);
                    mergedEvents.add(updatedEventDto);
                }
            } else {
                EventDto eventDto = EventToEventDto(latestEvent);
                mergedEvents.add(eventDto);
            }
        }

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), mergedEvents.size());

        List<EventDto> paginatedList = mergedEvents.subList(start, end);

        return new PageImpl<>(paginatedList, pageable, mergedEvents.size());
    }

    public Optional<Event> findFirstByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationInstanceIdAndInstanceFlowHeadersSourceApplicationIntegrationIdOrderByTimestampDesc(
            ManualEventDto manualEventDto
    ) {
        return eventRepository.
                findFirstByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationInstanceIdAndInstanceFlowHeadersSourceApplicationIntegrationIdOrderByTimestampDesc(
                        manualEventDto.getSourceApplicationId(),
                        manualEventDto.getSourceApplicationInstanceId(),
                        manualEventDto.getSourceApplicationIntegrationId()
                );
    }

    public Page<EventDto> findAllByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationInstanceId(
            Long sourceApplicationId,
            String sourceApplicationInstanceId,
            Pageable pageable
    ) {
        return convertPageOfEventIntoPageOfEventDto(
                eventRepository.findAllByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationInstanceId(
                        sourceApplicationId, sourceApplicationInstanceId, pageable
                )
        );
    }

    public Page<EventDto> findAllByInstanceFlowHeadersSourceApplicationIdIn(
            List<Long> sourceApplicationIds,
            Pageable pageable
    ) {
        return convertPageOfEventIntoPageOfEventDto(
                eventRepository.findAllByInstanceFlowHeadersSourceApplicationIdIn(
                        sourceApplicationIds,
                        pageable
                )
        );
    }

    private Page<EventDto> convertPageOfEventIntoPageOfEventDto(Page<Event> events) {
        return events.map(this::EventToEventDto);
    }

    private EventDto EventToEventDto(Event event) {
        return EventDto.builder()
                .instanceFlowHeaders(event.getInstanceFlowHeaders())
                .name(event.getName())
                .timestamp(event.getTimestamp())
                .type(event.getType())
                .applicationId(event.getApplicationId())
                .errors(event.getErrors())
                .build();
    }
}
