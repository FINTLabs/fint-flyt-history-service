//package no.fintlabs;
//
//import no.fintlabs.model.IntegrationStatistics;
//import no.fintlabs.model.Statistics;
//import no.fintlabs.repositories.EventRepository;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//
//import java.util.Collection;
//import java.util.List;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.when;
//
//class StatisticsServiceTest {
//
//    EventRepository eventRepository;
//    StatisticsService statisticsService;
//
//    @BeforeEach
//    void setup() {
//        eventRepository = mock(EventRepository.class);
//        statisticsService = new StatisticsService(eventRepository);
//    }
//
//    @Test
//    void shouldMapToStatistics() {
//        when(eventRepository.countDispatchedInstances()).thenReturn(184L);
//        when(eventRepository.countCurrentInstanceErrors()).thenReturn(3L);
//
//        Statistics statistics = statisticsService.getStatistics();
//
//        assertEquals(Statistics
//                .builder()
//                .dispatchedInstances(184L)
//                .currentErrors(3L)
//                .build(), statistics);
//    }
//
//    @Test
//    void shouldMapToIntegrationStatistics() {
//        when(eventRepository.countDispatchedInstancesPerIntegrationId()).thenReturn(List.of(
//                new EventRepository.IntegrationIdAndCount() {
//                    @Override
//                    public String getIntegrationId() {
//                        return "1";
//                    }
//
//                    @Override
//                    public long getCount() {
//                        return 27;
//                    }
//                },
//                new EventRepository.IntegrationIdAndCount() {
//                    @Override
//                    public String getIntegrationId() {
//                        return "3";
//                    }
//
//                    @Override
//                    public long getCount() {
//                        return 102;
//                    }
//                }
//        ));
//
//        when(eventRepository.countCurrentInstanceErrorsPerIntegrationId()).thenReturn(List.of(
//                new EventRepository.IntegrationIdAndCount() {
//                    @Override
//                    public String getIntegrationId() {
//                        return "3";
//                    }
//
//                    @Override
//                    public long getCount() {
//                        return 24;
//                    }
//                },
//                new EventRepository.IntegrationIdAndCount() {
//                    @Override
//                    public String getIntegrationId() {
//                        return "4";
//                    }
//
//                    @Override
//                    public long getCount() {
//                        return 1;
//                    }
//                }
//        ));
//
//        when(eventRepository.countAllInstancesPerIntegrationId()).thenReturn(List.of(
//                new EventRepository.IntegrationIdAndCount() {
//                    @Override
//                    public String getIntegrationId() {
//                        return "1";
//                    }
//
//                    @Override
//                    public long getCount() {
//                        return 27;
//                    }
//                },
//                new EventRepository.IntegrationIdAndCount() {
//                    @Override
//                    public String getIntegrationId() {
//                        return "3";
//                    }
//
//                    @Override
//                    public long getCount() {
//                        return 126L;
//                    }
//                },
//                new EventRepository.IntegrationIdAndCount() {
//                    @Override
//                    public String getIntegrationId() {
//                        return "4";
//                    }
//
//                    @Override
//                    public long getCount() {
//                        return 1;
//                    }
//                }
//        ));
//
//        Collection<IntegrationStatistics> integrationStatistics = statisticsService.getIntegrationStatistics();
//
//        assertEquals(3, integrationStatistics.size());
//        assertTrue(integrationStatistics.containsAll(List.of(
//                IntegrationStatistics.builder().sourceApplicationIntegrationId("1").dispatchedInstances(27L).currentErrors(0L).totalInstances(27L).build(),
//                IntegrationStatistics.builder().sourceApplicationIntegrationId("3").dispatchedInstances(102L).currentErrors(24L).totalInstances(126L).build(),
//                IntegrationStatistics.builder().sourceApplicationIntegrationId("4").dispatchedInstances(0L).currentErrors(1L).totalInstances(1L).build()
//        )));
//    }
//}
