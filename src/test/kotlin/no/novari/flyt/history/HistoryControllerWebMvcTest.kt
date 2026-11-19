package no.novari.flyt.history

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.validation.Validation
import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.history.exceptions.LatestStatusEventNotOfTypeErrorException
import no.novari.flyt.history.exceptions.NoPreviousStatusEventsFoundException
import no.novari.flyt.history.model.action.InstanceStatusTransferredOverrideAction
import no.novari.flyt.history.model.action.ManuallyProcessedEventAction
import no.novari.flyt.history.model.action.ManuallyRejectedEventAction
import no.novari.flyt.history.model.event.Event
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.event.EventType
import no.novari.flyt.history.repository.projections.IntegrationStatisticsProjection
import no.novari.flyt.history.validation.ValidationErrorsFormattingService
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.security.core.Authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

class HistoryControllerWebMvcTest {
    private val authorizationService: AuthorizationService = mock()
    private val eventService: EventService = mock()
    private val manualEventCreationService: ManualEventCreationService = mock()
    private val validationErrorsFormattingService = ValidationErrorsFormattingService()
    private val authentication: Authentication = mock()
    private val objectMapper: ObjectMapper =
        jacksonObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val controller =
            HistoryController(
                authorizationService,
                eventService,
                manualEventCreationService,
                Validation.buildDefaultValidatorFactory(),
                validationErrorsFormattingService,
            )

        mockMvc =
            MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(GlobalExceptionHandler(validationErrorsFormattingService))
                .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
                .setMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
                .build()
    }

    @Test
    fun `events endpoint returns stable content and last without page metadata`() {
        val pageable = PageRequest.of(0, 20)
        whenever(
            eventService.getAllEventsBySourceApplicationAggregateInstanceId(
                eq(1L),
                eq("integration-1"),
                eq("instance-1"),
                any(),
            ),
        ).thenReturn(PageImpl(listOf(Event()), pageable, 1))

        mockMvc
            .perform(
                get("/api/intern/instance-flow-tracking/events")
                    .param("sourceApplicationId", "1")
                    .param("sourceApplicationIntegrationId", "integration-1")
                    .param("sourceApplicationInstanceId", "instance-1")
                    .principal(authentication),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.last").value(true))
            .andExpect(jsonPath("$.pageable").doesNotExist())
            .andExpect(jsonPath("$.totalPages").doesNotExist())
            .andExpect(jsonPath("$.totalElements").doesNotExist())
    }

    @Test
    fun `events endpoint exposes audit fields with camel case json names`() {
        val pageable = PageRequest.of(0, 20)
        val oid = UUID.fromString("53134ef2-4480-46d6-99a3-1920d36ea333")
        val createdAt = Instant.parse("2024-01-01T12:34:56Z")
        whenever(
            eventService.getAllEventsBySourceApplicationAggregateInstanceId(
                eq(1L),
                eq("integration-1"),
                eq("instance-1"),
                any(),
            ),
        ).thenReturn(
            PageImpl(
                listOf(
                    Event(
                        category = EventCategory.INSTANCE_MANUALLY_PROCESSED,
                        timestamp = OffsetDateTime.parse("2024-01-01T12:30:00Z"),
                        type = EventType.INFO,
                        createdAt = createdAt,
                        createdBy = "Ola Nordmann",
                        createdByActor = Actor.User(oid),
                    ),
                ),
                pageable,
                1,
            ),
        )

        mockMvc
            .perform(
                get("/api/intern/instance-flow-tracking/events")
                    .param("sourceApplicationId", "1")
                    .param("sourceApplicationIntegrationId", "integration-1")
                    .param("sourceApplicationInstanceId", "instance-1")
                    .principal(authentication),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].createdAt").value("2024-01-01T12:34:56Z"))
            .andExpect(jsonPath("$.content[0].createdBy").value("Ola Nordmann"))
            .andExpect(jsonPath("$.content[0].createdByActor.type").value("USER"))
            .andExpect(jsonPath("$.content[0].createdByActor.oid").value(oid.toString()))
            .andExpect(jsonPath("$.content[0].created_at").doesNotExist())
            .andExpect(jsonPath("$.content[0].created_by").doesNotExist())
            .andExpect(jsonPath("$.content[0].created_by_actor").doesNotExist())
    }

    @Test
    fun `integration statistics endpoint returns stable content without slice metadata`() {
        val candidateSourceApplicationIds = setOf(1L)
        whenever(eventService.findDistinctSourceApplicationIds()).thenReturn(candidateSourceApplicationIds)
        whenever(
            authorizationService.getUserAuthorizedSourceApplicationIds(
                authentication,
                candidateSourceApplicationIds,
            ),
        ).thenReturn(setOf(1L))
        whenever(eventService.getIntegrationStatistics(any(), any()))
            .thenReturn(SliceImpl(listOf(testProjection()), PageRequest.of(0, 20), false))

        mockMvc
            .perform(
                get("/api/intern/instance-flow-tracking/statistics/integrations")
                    .principal(authentication),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].total").value(10))
            .andExpect(jsonPath("$.pageable").doesNotExist())
            .andExpect(jsonPath("$.last").doesNotExist())
    }

    @Test
    fun `integration statistics endpoint returns empty content when user has no authorized source applications`() {
        val candidateSourceApplicationIds = setOf(1L)
        whenever(eventService.findDistinctSourceApplicationIds()).thenReturn(candidateSourceApplicationIds)
        whenever(
            authorizationService.getUserAuthorizedSourceApplicationIds(
                authentication,
                candidateSourceApplicationIds,
            ),
        ).thenReturn(emptySet())

        mockMvc
            .perform(
                get("/api/intern/instance-flow-tracking/statistics/integrations")
                    .principal(authentication),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(0))
            .andExpect(jsonPath("$.pageable").doesNotExist())
    }

    @ParameterizedTest
    @EnumSource(
        value = EventCategory::class,
        names = [
            "INSTANCE_MANUALLY_PROCESSED",
            "INSTANCE_MANUALLY_REJECTED",
            "INSTANCE_STATUS_OVERRIDDEN_AS_TRANSFERRED",
        ],
    )
    fun `manual status accepts omitted null and long comments`(category: EventCategory) {
        whenever(manualEventCreationService.addManuallyProcessedEvent(any())).thenAnswer {
            Event(comment = it.getArgument<ManuallyProcessedEventAction>(0).comment)
        }
        whenever(manualEventCreationService.addManuallyRejectedEvent(any())).thenAnswer {
            Event(comment = it.getArgument<ManuallyRejectedEventAction>(0).comment)
        }
        whenever(manualEventCreationService.addInstanceStatusOverriddenAsTransferredEvent(any())).thenAnswer {
            Event(comment = it.getArgument<InstanceStatusTransferredOverrideAction>(0).comment)
        }

        val payload = objectMapper.valueToTree<ObjectNode>(validAction())
        if (category != EventCategory.INSTANCE_MANUALLY_PROCESSED) payload.remove("archiveInstanceId")
        payload.remove("comment")
        val request =
            post("/api/intern/instance-flow-tracking/events/${category.eventName}")
                .contentType(MediaType.APPLICATION_JSON)
                .principal(authentication)
        val longComment = "Avklart med saksbehandler. ".repeat(100)

        mockMvc
            .perform(request.content(payload.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.comment").value(nullValue()))
        mockMvc
            .perform(request.content(payload.putNull("comment").toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.comment").value(nullValue()))
        mockMvc
            .perform(request.content(payload.put("comment", longComment).toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.comment").value(longComment))
    }

    @Test
    fun `manually processed returns 422 with formatted message on validation error`() {
        mockMvc
            .perform(
                post("/api/intern/instance-flow-tracking/events/instance-manually-processed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validAction().copy(sourceApplicationId = 0L)))
                    .principal(authentication),
            ).andExpect(status().isUnprocessableEntity)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(422))
            .andExpect(jsonPath("$.detail").value("Validation error: ['sourceApplicationId must be greater than 0']"))
    }

    @Test
    fun `manually processed returns 404 when no previous event exists`() {
        whenever(manualEventCreationService.addManuallyProcessedEvent(any()))
            .thenThrow(NoPreviousStatusEventsFoundException())

        mockMvc
            .perform(
                post("/api/intern/instance-flow-tracking/events/instance-manually-processed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validAction()))
                    .principal(authentication),
            ).andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("No previous event found"))
    }

    @Test
    fun `manually processed returns 400 when latest event is not of type error`() {
        whenever(manualEventCreationService.addManuallyProcessedEvent(any()))
            .thenThrow(LatestStatusEventNotOfTypeErrorException())

        mockMvc
            .perform(
                post("/api/intern/instance-flow-tracking/events/instance-manually-processed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validAction()))
                    .principal(authentication),
            ).andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("Previous event status is not of type ERROR"))
    }

    @Test
    fun `events endpoint propagates forbidden status from authorization as problem detail`() {
        whenever(
            authorizationService.validateUserIsAuthorizedForSourceApplication(any(), eq(1L)),
        ).thenThrow(ResponseStatusException(HttpStatus.FORBIDDEN, "No access"))

        mockMvc
            .perform(
                get("/api/intern/instance-flow-tracking/events")
                    .param("sourceApplicationId", "1")
                    .param("sourceApplicationIntegrationId", "integration-1")
                    .param("sourceApplicationInstanceId", "instance-1")
                    .principal(authentication),
            ).andExpect(status().isForbidden)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.detail").value("No access"))
    }

    private fun validAction() =
        ManuallyProcessedEventAction(
            sourceApplicationId = 1L,
            sourceApplicationIntegrationId = "integration-1",
            sourceApplicationInstanceId = "instance-1",
            archiveInstanceId = "archive-1",
        )

    private fun testProjection() =
        object : IntegrationStatisticsProjection {
            override fun getSourceApplicationId() = 1L

            override fun getIntegrationId() = 2L

            override fun getTotal() = 10L

            override fun getInProgress() = 1L

            override fun getTransferred() = 8L

            override fun getAborted() = 0L

            override fun getFailed() = 1L
        }
}
