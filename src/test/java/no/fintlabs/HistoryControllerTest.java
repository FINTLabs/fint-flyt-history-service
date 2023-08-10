package no.fintlabs;

import no.fintlabs.model.Event;
import no.fintlabs.repositories.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

public class HistoryControllerTest {

    @InjectMocks
    private HistoryController historyController;

    @Mock
    private EventRepository eventRepository;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testGetEvents() {
        Event event = new Event();
        List<Event> events = List.of(event);
        Page<Event> page = new PageImpl<>(events);
        when(eventRepository.findAll(any(PageRequest.class))).thenReturn(page);

        ResponseEntity<Page<Event>> response = historyController.
                getEvents(0, 10, "id", Sort.Direction.ASC, Optional.empty());

        assertEquals(page, response.getBody());
    }

    @Test
    void testGetEvents_onlyLatestPerInstanceTrue() {
        Page<Event> eventPage = new PageImpl<>(Collections.emptyList());
        when(eventRepository.findLatestEventPerSourceApplicationInstanceId(any())).thenReturn(eventPage);

        ResponseEntity<Page<Event>> response = historyController.
                getEvents(0, 10, "id", Sort.Direction.ASC, Optional.of(true));

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(eventPage, response.getBody());
    }

    @Test
    void testGetEventsWithInstanceId() {
        Page<Event> eventPage = new PageImpl<>(Collections.emptyList());
        when(eventRepository.findAllByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationInstanceId(anyLong(), anyString(), any())).thenReturn(eventPage);

        ResponseEntity<Page<Event>> response = historyController.getEventsWithInstanceId(0, 10, "id", Sort.Direction.ASC, 1L, "instanceId");

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(eventPage, response.getBody());
    }

}
