//package no.fintlabs;
//
//import no.fintlabs.model.EventDto;
//import no.fintlabs.model.InstanceSearchParameters;
//import no.fintlabs.repository.projections.statistics.IntegrationStatistics;
//import no.fintlabs.repository.projections.statistics.Statistics;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.MockitoAnnotations;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageImpl;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Sort;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.core.Authentication;
//
//import java.util.*;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.when;
//
//public class HistoryControllerTest {
//
//    @InjectMocks
//    private HistoryController historyController;
//
//    @Mock
//    private EventService eventService;
//
//    @Mock
//    private StatisticsService statisticsService;
//
//    @Mock
//    Authentication authentication;
//
//    private InstanceSearchParameters instanceSearchParameters;
//
//    @BeforeEach
//    public void setUp() {
//        MockitoAnnotations.openMocks(this);
//        instanceSearchParameters = new InstanceSearchParameters(
//                Optional.empty(),
//                Optional.empty()
//        );
//    }
//
//    @Test
//    public void testGetEvents() {
//        EventDto event = new EventDto();
//        List<EventDto> events = List.of(event);
//        Page<EventDto> page = new PageImpl<>(events);
//        when(eventService.findAll(any(PageRequest.class))).thenReturn(page);
//
//        ResponseEntity<Page<EventDto>> response = historyController.
//                getEvents(
//                        authentication,
//                        0,
//                        10,
//                        "id",
//                        Sort.Direction.ASC,
//                        Optional.empty(),
//                        instanceSearchParameters
//                );
//
//        assertEquals(page, response.getBody());
//    }
//
//    @Test
//    void testGetEvents_onlyLatestPerInstanceTrue() {
//        Page<EventDto> eventPage = new PageImpl<>(Collections.emptyList());
//        when(eventService.getMergedLatestEvents(any(), any())).thenReturn(eventPage);
//
//        ResponseEntity<Page<EventDto>> response = historyController.
//                getEvents(
//                        authentication,
//                        0,
//                        10,
//                        "id",
//                        Sort.Direction.ASC,
//                        Optional.of(true),
//                        instanceSearchParameters
//                );
//
//        assertEquals(200, response.getStatusCodeValue());
//        assertEquals(eventPage, response.getBody());
//    }
//
//    @Test
//    void testGetEventsWithInstanceId() {
//        Page<EventDto> eventPage = new PageImpl<>(Collections.emptyList());
//        when(eventService
//                .findAllByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationInstanceId(
//                        anyLong(), anyString(), any()
//                )
//        ).thenReturn(eventPage);
//
//        ResponseEntity<Page<EventDto>> response = historyController.getEventsWithInstanceId(authentication, 0, 10, "id", Sort.Direction.ASC, 1L, "instanceId");
//
//        assertEquals(200, response.getStatusCodeValue());
//        assertEquals(eventPage, response.getBody());
//    }
//
//    @Test
//    void testGetStatistics() {
//        Statistics expectedStatistics = Statistics.builder()
//                .dispatchedInstances(10L)
//                .currentErrors(5L)
//                .build();
//
//        when(statisticsService.getStatistics()).thenReturn(expectedStatistics);
//
//        ResponseEntity<Statistics> response = historyController.getStatistics(authentication);
//
//        assertEquals(200, response.getStatusCodeValue());
//        assertEquals(expectedStatistics, response.getBody());
//    }
//
//    @Test
//    void testGetIntegrationStatistics() {
//        IntegrationStatistics integrationStatistics1 = IntegrationStatistics.builder()
//                .sourceApplicationIntegrationId("app1")
//                .dispatchedInstances(10L)
//                .currentErrors(5L)
//                .build();
//
//        IntegrationStatistics integrationStatistics2 = IntegrationStatistics.builder()
//                .sourceApplicationIntegrationId("app2")
//                .dispatchedInstances(8L)
//                .currentErrors(3L)
//                .build();
//
//        List<IntegrationStatistics> expectedList = Arrays.asList(integrationStatistics1, integrationStatistics2);
//        when(statisticsService.getIntegrationStatistics()).thenReturn(expectedList);
//
//        ResponseEntity<Collection<IntegrationStatistics>> response = historyController.getIntegrationStatistics(authentication);
//
//        assertEquals(200, response.getStatusCodeValue());
//        assertEquals(expectedList, response.getBody());
//    }
//
//}
