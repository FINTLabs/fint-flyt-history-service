package no.novari.flyt.history.repository

import no.novari.flyt.history.model.SourceApplicationAggregateInstanceId
import no.novari.flyt.history.model.event.EventCategorizationService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.event.EventType
import no.novari.flyt.history.repository.entities.EventEntity
import no.novari.flyt.history.repository.entities.InstanceFlowHeadersEmbeddable
import no.novari.flyt.history.repository.filters.IntegrationStatisticsQueryFilter
import no.novari.flyt.history.repository.projections.InstanceStatisticsProjection
import no.novari.flyt.history.repository.projections.IntegrationStatisticsProjection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.unit.DataSize
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EventRepositoryTest {
    @Autowired
    lateinit var eventRepository: EventRepository

    private val eventCategorizationService = EventCategorizationService()

    @BeforeEach
    fun setup() {
        eventRepository.deleteAll()
    }

    @Nested
    inner class FindLatestStatusEventBySourceApplicationAggregateInstanceId {
        @Test
        fun givenNoEvents_whenFindLatestStatusEventBySourceApplicationAggregateInstanceId_thenReturnEmpty() {
            val latestStatusEvent =
                eventRepository.findLatestStatusEventBySourceApplicationAggregateInstanceId(
                    aggregateId(1L, "testSourceApplicationIntegrationId1", "testSourceApplicationInstanceId1"),
                    eventCategorizationService.allInstanceStatusEventNames,
                )

            assertThat(latestStatusEvent).isNull()
        }

        @Test
        fun givenNoEventsWithMatchingAggregateId_whenFindLatestStatusEvent_thenReturnEmpty() {
            save(
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId2",
                    "testName1",
                    odt(2024, 1, 1, 12, 0),
                    archiveInstanceId = "testArchiveInstanceId1",
                ),
            )

            val latestStatusEvent =
                eventRepository.findLatestStatusEventBySourceApplicationAggregateInstanceId(
                    aggregateId(1L, "testSourceApplicationIntegrationId1", "testSourceApplicationInstanceId1"),
                    eventCategorizationService.allInstanceStatusEventNames,
                )

            assertThat(latestStatusEvent).isNull()
        }

        @Test
        fun givenInstanceWithInstanceDispatchedFollowedByInstanceStatusOverriddenShouldReturnArchiveInstanceId() {
            save(
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 12, 0),
                    archiveInstanceId = "testArchiveInstanceId1",
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_STATUS_OVERRIDDEN_AS_TRANSFERRED.eventName,
                    odt(2024, 1, 1, 12, 0, 1),
                ),
            )

            val archiveInstanceIds =
                eventRepository.findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                )

            assertThat(archiveInstanceIds).isEqualTo(listOf("testArchiveInstanceId1"))
        }

        @Test
        fun givenEventsWithMatchingSourceApplicationAggregateInstanceId_whenCalled_thenReturnLatestStatusEvent() {
            save(
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId0",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 12, 0),
                    archiveInstanceId = "testArchiveInstanceId1",
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 12, 0),
                    archiveInstanceId = "testArchiveInstanceId1",
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 12, 1),
                    archiveInstanceId = "testArchiveInstanceId1",
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId2",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 14, 0),
                    archiveInstanceId = "testArchiveInstanceId1",
                ),
            )

            val returnedEvent =
                eventRepository.findLatestStatusEventBySourceApplicationAggregateInstanceId(
                    aggregateId(1L, "testSourceApplicationIntegrationId1", "testSourceApplicationInstanceId1"),
                    eventCategorizationService.allInstanceStatusEventNames,
                )

            assertThat(returnedEvent)
                .usingRecursiveComparison()
                .ignoringFields("id")
                .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime::class.java)
                .isEqualTo(
                    event(
                        1L,
                        "testSourceApplicationIntegrationId1",
                        "testSourceApplicationInstanceId1",
                        EventCategory.INSTANCE_DISPATCHED.eventName,
                        odt(2024, 1, 1, 12, 1),
                        archiveInstanceId = "testArchiveInstanceId1",
                    ),
                )
        }
    }

    @Nested
    inner class FindArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc {
        @Test
        fun givenFullAggregateIdAndMatchingEventsShouldReturnArchiveInstanceIdsFromEvents() {
            save(
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 12, 0),
                    archiveInstanceId = "testArchiveInstanceId1",
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId2",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 13, 0),
                    archiveInstanceId = "testArchiveInstanceId2",
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId2",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 14, 0),
                    archiveInstanceId = "testArchiveInstanceId3",
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId2",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 10, 0),
                    archiveInstanceId = "testArchiveInstanceId4",
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId2",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 15, 0),
                    archiveInstanceId = "testArchiveInstanceId5",
                ),
                event(
                    2L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 16, 0),
                    archiveInstanceId = "testArchiveInstanceId6",
                ),
            )

            val archiveInstanceIds =
                eventRepository.findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
                    1L,
                    "testSourceApplicationIntegrationId2",
                    "testSourceApplicationInstanceId1",
                )

            assertThat(
                archiveInstanceIds,
            ).containsExactly("testArchiveInstanceId5", "testArchiveInstanceId3", "testArchiveInstanceId4")
        }

        @Test
        fun givenNoSourceApplicationIntegrationIdIdAndMatchingEventsShouldReturnArchiveInstanceIdsFromEvents() {
            save(
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 12, 0),
                    archiveInstanceId = "testArchiveInstanceId1",
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId2",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 13, 0),
                    archiveInstanceId = "testArchiveInstanceId2",
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId2",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 14, 0),
                    archiveInstanceId = "testArchiveInstanceId3",
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId2",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 10, 0),
                    archiveInstanceId = "testArchiveInstanceId4",
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId2",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 15, 0),
                    archiveInstanceId = "testArchiveInstanceId5",
                ),
                event(
                    2L,
                    "testSourceApplicationIntegrationId2",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 16, 0),
                    archiveInstanceId = "testArchiveInstanceId6",
                ),
            )

            val archiveInstanceIds =
                eventRepository.findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
                    1L,
                    null,
                    "testSourceApplicationInstanceId1",
                )

            assertThat(
                archiveInstanceIds,
            ).containsExactly(
                "testArchiveInstanceId5",
                "testArchiveInstanceId3",
                "testArchiveInstanceId1",
                "testArchiveInstanceId4",
            )
        }

        @Test
        fun givenNoMatchingEventsShouldReturnEmptyList() {
            save(
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 12, 0),
                    archiveInstanceId = "testArchiveInstanceId1",
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId2",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 13, 0),
                    archiveInstanceId = "testArchiveInstanceId2",
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId2",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 14, 0),
                    archiveInstanceId = "testArchiveInstanceId3",
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId2",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 10, 0),
                    archiveInstanceId = "testArchiveInstanceId4",
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId2",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 15, 0),
                    archiveInstanceId = "testArchiveInstanceId5",
                ),
                event(
                    2L,
                    "testSourceApplicationIntegrationId2",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 16, 0),
                    archiveInstanceId = "testArchiveInstanceId6",
                ),
            )

            val archiveInstanceIds =
                eventRepository.findArchiveInstanceIdBySourceApplicationAggregateInstanceIdOrderByTimestampDesc(
                    3L,
                    null,
                    "testSourceApplicationInstanceId1",
                )

            assertThat(archiveInstanceIds).isEmpty()
        }
    }

    @Nested
    inner class GetTotalStatistics {
        @Test
        fun givenNullSourceApplicationIdListShouldReturnEmptyStatistics() {
            save(
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_RECEIVED.eventName,
                    odt(2024, 1, 1, 12, 0),
                    archiveInstanceId = "testArchiveInstanceId1",
                ),
            )

            val totalStatistics =
                EventRepository::class.java
                    .getMethod(
                        "getTotalStatistics",
                        Collection::class.java,
                        eventCategorizationService.eventNamesPerInstanceStatus::class.java,
                    ).invoke(
                        eventRepository,
                        null,
                        eventCategorizationService.eventNamesPerInstanceStatus,
                    ) as InstanceStatisticsProjection

            assertStatistics(totalStatistics, 0, 0, 0, 0, 0)
        }

        @Test
        fun givenEmptySourceApplicationIdListShouldReturnEmptyStatistics() {
            save(
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_RECEIVED.eventName,
                    odt(2024, 1, 1, 12, 0),
                    archiveInstanceId = "testArchiveInstanceId1",
                ),
            )
            assertStatistics(
                eventRepository.getTotalStatistics(listOf(), eventCategorizationService.eventNamesPerInstanceStatus),
                0,
                0,
                0,
                0,
                0,
            )
        }

        @Test
        fun givenNoSourceApplicationFilterShouldReturnStatisticsForAllSourceApplications() {
            save(
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_RECEIVED.eventName,
                    odt(2024, 1, 1, 12, 0),
                ),
                event(
                    2L,
                    "testSourceApplicationIntegrationId2",
                    "testSourceApplicationInstanceId2",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 13, 0),
                    archiveInstanceId = "testArchiveInstanceId2",
                ),
            )

            assertStatistics(
                eventRepository.getTotalStatisticsForAllSourceApplications(
                    eventCategorizationService.eventNamesPerInstanceStatus,
                ),
                2,
                1,
                1,
                0,
                0,
            )
        }

        @Test
        fun givenNoEventsWithMatchingSourceApplicationIdShouldReturnZeroes() {
            save(
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_RECEIVED.eventName,
                    odt(2024, 1, 1, 12, 0),
                    archiveInstanceId = "testArchiveInstanceId1",
                ),
            )
            assertStatistics(
                eventRepository.getTotalStatistics(listOf(2L), eventCategorizationService.eventNamesPerInstanceStatus),
                0,
                0,
                0,
                0,
                0,
            )
        }

        @Test
        fun givenSingleInProgressInstanceWithMatchingSourceApplicationIdShouldReturnOneInProgress() {
            save(
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_RECEIVED.eventName,
                    odt(2024, 1, 1, 12, 0),
                    archiveInstanceId = "testArchiveInstanceId1",
                ),
            )
            assertStatistics(
                eventRepository.getTotalStatistics(listOf(1L), eventCategorizationService.eventNamesPerInstanceStatus),
                1,
                1,
                0,
                0,
                0,
            )
        }

        @Test
        fun givenSingleTransferredInstanceWithMatchingSourceApplicationIdShouldReturnOneTransferred() {
            save(
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_RECEIVED.eventName,
                    odt(2024, 1, 1, 12, 0),
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 13, 0),
                    archiveInstanceId = "testArchiveInstanceId1",
                ),
            )
            assertStatistics(
                eventRepository.getTotalStatistics(listOf(1L), eventCategorizationService.eventNamesPerInstanceStatus),
                1,
                0,
                1,
                0,
                0,
            )
        }

        @Test
        fun givenSingleAbortedInstanceWithMatchingSourceApplicationIdShouldReturnOneAborted() {
            save(
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_RECEIVED.eventName,
                    odt(2024, 1, 1, 12, 0),
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_MANUALLY_REJECTED.eventName,
                    odt(2024, 1, 1, 13, 0),
                    archiveInstanceId = "testArchiveInstanceId1",
                ),
            )
            assertStatistics(
                eventRepository.getTotalStatistics(listOf(1L), eventCategorizationService.eventNamesPerInstanceStatus),
                1,
                0,
                0,
                1,
                0,
            )
        }

        @Test
        fun givenSingleFailedInstanceWithMatchingSourceApplicationIdShouldReturnOneFailed() {
            save(
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_RECEIVED.eventName,
                    odt(2024, 1, 1, 12, 0),
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_MAPPING_ERROR.eventName,
                    odt(2024, 1, 1, 13, 0),
                    archiveInstanceId = "testArchiveInstanceId1",
                ),
            )
            assertStatistics(
                eventRepository.getTotalStatistics(listOf(1L), eventCategorizationService.eventNamesPerInstanceStatus),
                1,
                0,
                0,
                0,
                1,
            )
        }

        @Test
        fun givenInstancesOfAllStatusesWithMatchingSourceApplicationIdShouldReturnStatisticsForAllStatuses() {
            save(
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_RECEIVED.eventName,
                    odt(2024, 1, 1, 12, 0),
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId2",
                    EventCategory.INSTANCE_RECEIVED.eventName,
                    odt(2024, 1, 1, 12, 0),
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId3",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 12, 0),
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId4",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 12, 0),
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId5",
                    EventCategory.INSTANCE_MANUALLY_REJECTED.eventName,
                    odt(2024, 1, 1, 12, 0),
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId6",
                    EventCategory.INSTANCE_MANUALLY_REJECTED.eventName,
                    odt(2024, 1, 1, 12, 0),
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId7",
                    EventCategory.INSTANCE_MAPPING_ERROR.eventName,
                    odt(2024, 1, 1, 12, 0),
                    type = EventType.ERROR,
                ),
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId8",
                    EventCategory.INSTANCE_MAPPING_ERROR.eventName,
                    odt(2024, 1, 1, 12, 0),
                    type = EventType.ERROR,
                ),
            )

            assertStatistics(
                eventRepository.getTotalStatistics(listOf(1L), eventCategorizationService.eventNamesPerInstanceStatus),
                8,
                2,
                2,
                2,
                2,
            )
        }
    }

    @Nested
    inner class GetIntegrationStatistics {
        @Test
        fun givenNullFilterShouldReturnStatisticsForAll() {
            save(
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_RECEIVED.eventName,
                    odt(2024, 1, 1, 12, 0),
                    integrationId = 1L,
                ),
            )

            val integrationStatistics =
                eventRepository.getIntegrationStatistics(
                    null,
                    eventCategorizationService.eventNamesPerInstanceStatus,
                    Pageable.unpaged(),
                )

            assertIntegrationStatistics(integrationStatistics, listOf(StatsExpectation(1, 1, 1, 0, 0, 0)))
        }

        @Test
        fun givenFilterWithNullValuesShouldReturnStatisticsForAll() {
            save(
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_RECEIVED.eventName,
                    odt(2024, 1, 1, 12, 0),
                    integrationId = 1L,
                ),
            )

            val integrationStatistics =
                eventRepository.getIntegrationStatistics(
                    IntegrationStatisticsQueryFilter
                        .builder()
                        .sourceApplicationIds(
                            null,
                        ).sourceApplicationIntegrationIds(null)
                        .integrationIds(null)
                        .build(),
                    eventCategorizationService.eventNamesPerInstanceStatus,
                    Pageable.unpaged(),
                )

            assertIntegrationStatistics(integrationStatistics, listOf(StatsExpectation(1, 1, 1, 0, 0, 0)))
        }

        @Test
        fun givenFilterWithEmptyValuesShouldReturnStatisticsForAll() {
            save(
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_RECEIVED.eventName,
                    odt(2024, 1, 1, 12, 0),
                    integrationId = 1L,
                ),
            )

            val integrationStatistics =
                eventRepository.getIntegrationStatistics(
                    IntegrationStatisticsQueryFilter
                        .builder()
                        .sourceApplicationIds(
                            listOf(),
                        ).sourceApplicationIntegrationIds(listOf())
                        .integrationIds(listOf())
                        .build(),
                    eventCategorizationService.eventNamesPerInstanceStatus,
                    Pageable.unpaged(),
                )

            assertIntegrationStatistics(integrationStatistics, listOf(StatsExpectation(1, 1, 1, 0, 0, 0)))
        }

        @Test
        fun givenFilterWithMatchingSourceApplicationIdShouldReturnStatisticsForSourceApplicationId() {
            save(
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_RECEIVED.eventName,
                    odt(2024, 1, 1, 12, 0),
                    integrationId = 1L,
                ),
                event(
                    2L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_RECEIVED.eventName,
                    odt(2024, 1, 1, 12, 0),
                    integrationId = 2L,
                ),
            )

            val integrationStatistics =
                eventRepository.getIntegrationStatistics(
                    IntegrationStatisticsQueryFilter.builder().sourceApplicationIds(listOf(2L)).build(),
                    eventCategorizationService.eventNamesPerInstanceStatus,
                    Pageable.unpaged(),
                )

            assertIntegrationStatistics(integrationStatistics, listOf(StatsExpectation(2, 1, 1, 0, 0, 0)))
        }

        @Test
        fun givenFilterWithMatchingSourceApplicationIntegrationIdShouldReturnStatisticsForSourceApplicationId() {
            save(
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_RECEIVED.eventName,
                    odt(2024, 1, 1, 12, 0),
                    integrationId = 1L,
                ),
                event(
                    2L,
                    "testSourceApplicationIntegrationId2",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_RECEIVED.eventName,
                    odt(2024, 1, 1, 12, 0),
                    integrationId = 2L,
                ),
            )

            val integrationStatistics =
                eventRepository.getIntegrationStatistics(
                    IntegrationStatisticsQueryFilter
                        .builder()
                        .sourceApplicationIntegrationIds(
                            listOf("testSourceApplicationIntegrationId2"),
                        ).build(),
                    eventCategorizationService.eventNamesPerInstanceStatus,
                    Pageable.unpaged(),
                )

            assertIntegrationStatistics(integrationStatistics, listOf(StatsExpectation(2, 1, 1, 0, 0, 0)))
        }

        @Test
        fun givenFilterWithMatchingIntegrationIdShouldReturnStatisticsForIntegrationId() {
            save(
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_RECEIVED.eventName,
                    odt(2024, 1, 1, 12, 0),
                    integrationId = 1L,
                ),
                event(
                    2L,
                    "testSourceApplicationIntegrationId2",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_RECEIVED.eventName,
                    odt(2024, 1, 1, 12, 0),
                    integrationId = 2L,
                ),
            )

            val integrationStatistics =
                eventRepository.getIntegrationStatistics(
                    IntegrationStatisticsQueryFilter.builder().integrationIds(listOf(2L)).build(),
                    eventCategorizationService.eventNamesPerInstanceStatus,
                    Pageable.unpaged(),
                )

            assertIntegrationStatistics(integrationStatistics, listOf(StatsExpectation(2, 1, 1, 0, 0, 0)))
        }

        @Test
        fun givenFilterWithNoMatchingEventsShouldReturnNoStatistics() {
            save(
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_RECEIVED.eventName,
                    odt(2024, 1, 1, 12, 0),
                    integrationId = 1L,
                ),
                event(
                    2L,
                    "testSourceApplicationIntegrationId2",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_RECEIVED.eventName,
                    odt(2024, 1, 1, 12, 0),
                    integrationId = 2L,
                ),
            )

            val integrationStatistics =
                eventRepository.getIntegrationStatistics(
                    IntegrationStatisticsQueryFilter.builder().integrationIds(listOf(3L)).build(),
                    eventCategorizationService.eventNamesPerInstanceStatus,
                    Pageable.unpaged(),
                )

            assertThat(integrationStatistics).hasSize(0)
        }

        @Test
        fun givenFilterWithMultipleMatchingEventsShouldReturnStatisticsForAllMatchingEvents() {
            save(
                event(
                    1L,
                    "testSourceApplicationIntegrationId1",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_RECEIVED.eventName,
                    odt(2024, 1, 1, 12, 0),
                    integrationId = 1L,
                ),
                event(
                    2L,
                    "testSourceApplicationIntegrationId2",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_RECEIVAL_ERROR.eventName,
                    odt(2024, 1, 1, 12, 0),
                    integrationId = 2L,
                ),
                event(
                    2L,
                    "testSourceApplicationIntegrationId2",
                    "testSourceApplicationInstanceId2",
                    EventCategory.INSTANCE_DISPATCHED.eventName,
                    odt(2024, 1, 1, 12, 0),
                    integrationId = 2L,
                ),
                event(
                    2L,
                    "testSourceApplicationIntegrationId3",
                    "testSourceApplicationInstanceId1",
                    EventCategory.INSTANCE_MANUALLY_REJECTED.eventName,
                    odt(2024, 1, 1, 12, 0),
                    integrationId = 3L,
                ),
            )

            val integrationStatistics =
                eventRepository.getIntegrationStatistics(
                    IntegrationStatisticsQueryFilter
                        .builder()
                        .sourceApplicationIds(listOf(1L, 2L, 3L))
                        .sourceApplicationIntegrationIds(
                            listOf(
                                "testSourceApplicationIntegrationId1",
                                "testSourceApplicationIntegrationId2",
                                "testSourceApplicationIntegrationId3",
                            ),
                        ).integrationIds(listOf(1L, 2L, 3L))
                        .build(),
                    eventCategorizationService.eventNamesPerInstanceStatus,
                    Pageable.unpaged(),
                )

            assertIntegrationStatistics(
                integrationStatistics,
                listOf(
                    StatsExpectation(1, 1, 1, 0, 0, 0),
                    StatsExpectation(2, 2, 0, 1, 0, 1),
                    StatsExpectation(3, 1, 0, 0, 1, 0),
                ),
            )
        }
    }

    companion object {
        @JvmField
        @Container
        val postgreSQLContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:17")
                .withUrlParam("reWriteBatchedInserts", "true")
                .withCreateContainerCmdModifier { createContainerCmd ->
                    requireNotNull(createContainerCmd.hostConfig)
                        .withCpuCount(2L)
                        .withMemory(DataSize.ofGigabytes(8).toBytes())
                }

        @JvmStatic
        @DynamicPropertySource
        fun postgreSQLProperties(registry: DynamicPropertyRegistry) {
            postgreSQLContainer.start()
            registry.add("fint.database.url", postgreSQLContainer::getJdbcUrl)
            registry.add("fint.database.username", postgreSQLContainer::getUsername)
            registry.add("fint.database.password", postgreSQLContainer::getPassword)
        }
    }

    private fun aggregateId(
        sourceApplicationId: Long,
        sourceApplicationIntegrationId: String?,
        sourceApplicationInstanceId: String,
    ): SourceApplicationAggregateInstanceId {
        return object : SourceApplicationAggregateInstanceId {
            override val sourceApplicationId = sourceApplicationId
            override val sourceApplicationIntegrationId = sourceApplicationIntegrationId
            override val sourceApplicationInstanceId = sourceApplicationInstanceId
        }
    }

    private fun save(vararg events: EventEntity) {
        eventRepository.saveAllAndFlush(events.toList())
    }

    private fun event(
        sourceApplicationId: Long,
        sourceApplicationIntegrationId: String,
        sourceApplicationInstanceId: String,
        name: String,
        timestamp: OffsetDateTime,
        archiveInstanceId: String? = null,
        integrationId: Long? = null,
        type: EventType = EventType.INFO,
    ): EventEntity {
        return EventEntity
            .builder()
            .instanceFlowHeaders(
                InstanceFlowHeadersEmbeddable
                    .builder()
                    .sourceApplicationId(sourceApplicationId)
                    .sourceApplicationIntegrationId(sourceApplicationIntegrationId)
                    .sourceApplicationInstanceId(sourceApplicationInstanceId)
                    .archiveInstanceId(archiveInstanceId)
                    .integrationId(integrationId)
                    .build(),
            ).name(name)
            .timestamp(timestamp)
            .type(type)
            .build()
    }

    private fun odt(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0,
        nano: Int = 0,
    ): OffsetDateTime = OffsetDateTime.of(year, month, day, hour, minute, second, nano, ZoneOffset.UTC)

    private fun assertStatistics(
        statistics: InstanceStatisticsProjection,
        total: Int,
        inProgress: Int,
        transferred: Int,
        aborted: Int,
        failed: Int,
    ) {
        assertThat(statistics.getTotal()).isEqualTo(total.toLong())
        assertThat(statistics.getInProgress()).isEqualTo(inProgress.toLong())
        assertThat(statistics.getTransferred()).isEqualTo(transferred.toLong())
        assertThat(statistics.getAborted()).isEqualTo(aborted.toLong())
        assertThat(statistics.getFailed()).isEqualTo(failed.toLong())
    }

    private fun assertIntegrationStatistics(
        integrationStatistics: Slice<IntegrationStatisticsProjection>,
        expected: List<StatsExpectation>,
    ) {
        assertThat(integrationStatistics).hasSize(expected.size)
        val sorted = integrationStatistics.content.sortedBy { it.getIntegrationId() }

        expected.forEachIndexed { index, expectation ->
            val actual = sorted[index]
            assertThat(actual.getIntegrationId()).isEqualTo(expectation.integrationId.toLong())
            assertThat(actual.getTotal()).isEqualTo(expectation.total.toLong())
            assertThat(actual.getInProgress()).isEqualTo(expectation.inProgress.toLong())
            assertThat(actual.getTransferred()).isEqualTo(expectation.transferred.toLong())
            assertThat(actual.getAborted()).isEqualTo(expectation.aborted.toLong())
            assertThat(actual.getFailed()).isEqualTo(expectation.failed.toLong())
        }
    }

    private data class StatsExpectation(
        val integrationId: Int,
        val total: Int,
        val inProgress: Int,
        val transferred: Int,
        val aborted: Int,
        val failed: Int,
    )
}
