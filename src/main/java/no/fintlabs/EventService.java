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
        Page<Event> latestEventsPage = eventRepository
                .findLatestEventPerSourceApplicationInstanceId(pageable);

        Page<Event> latestNonDeletedEventsPage = eventRepository
                .findLatestEventNotDeletedPerSourceApplicationInstanceId(pageable);

        long totalLatestEvents = eventRepository.countLatestEventPerSourceApplicationInstanceId();
        long totalLatestNonDeletedEvents = eventRepository.countLatestEventNotDeletedPerSourceApplicationInstanceId();

        long totalElements = totalLatestEvents + totalLatestNonDeletedEvents;

        List<EventDto> mergedEvents = mergeEvents(latestEventsPage.getContent(), latestNonDeletedEventsPage.getContent());

        return new PageImpl<>(mergedEvents, pageable, totalElements);
    }

    public Page<EventDto> getMergedLatestEventsWhereSourceApplicationIdIn(
            List<Long> sourceApplicationIds,
            Pageable pageable
    ) {
        Page<Event> latestEventsPage = eventRepository
                .findLatestEventPerSourceApplicationInstanceIdAndSourceApplicationIdIn(
                        sourceApplicationIds,
                        pageable
                );

        Page<Event> latestNonDeletedEventsPage = eventRepository
                .findLatestEventNotDeletedPerSourceApplicationInstanceIdAndSourceApplicationIdIn(
                        sourceApplicationIds,
                        pageable
                );

        long totalLatestEvents = eventRepository.countLatestEventPerSourceApplicationInstanceIdAndSourceApplicationIdIn(sourceApplicationIds);
        long totalLatestNonDeletedEvents = eventRepository.countLatestEventNotDeletedPerSourceApplicationInstanceIdAndSourceApplicationIdIn(sourceApplicationIds);

        long totalElements = totalLatestEvents + totalLatestNonDeletedEvents;

        List<EventDto> mergedEvents = mergeEvents(latestEventsPage.getContent(), latestNonDeletedEventsPage.getContent());

        return new PageImpl<>(mergedEvents, pageable, totalElements);
    }

    private List<EventDto> mergeEvents(List<Event> latestEvents, List<Event> latestNonDeletedEvents) {
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

        return mergedEvents;
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
