package no.novari.flyt.history

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.validation.ConstraintViolation
import jakarta.validation.Validator
import jakarta.validation.ValidatorFactory
import no.novari.flyt.history.exceptions.LatestStatusEventNotOfTypeErrorException
import no.novari.flyt.history.exceptions.NoPreviousStatusEventsFoundException
import no.novari.flyt.history.model.action.ManuallyProcessedEventAction
import no.novari.flyt.history.model.event.Event
import no.novari.flyt.history.repository.projections.IntegrationStatisticsProjection
import no.novari.flyt.history.validation.ValidationErrorsFormattingService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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

class HistoryControllerWebMvcTest {
    private val authorizationService: AuthorizationService = mock()
    private val eventService: EventService = mock()
    private val manualEventCreationService: ManualEventCreationService = mock()
    private val validationErrorsFormattingService: ValidationErrorsFormattingService = mock()
    private val validator: Validator = mock()
    private val authentication: Authentication = mock()
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val validatorFactory = mock<ValidatorFactory>()
        whenever(validatorFactory.validator).thenReturn(validator)

        val controller =
            HistoryController(
                authorizationService,
                eventService,
                manualEventCreationService,
                validatorFactory,
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

    @Test
    fun `manually processed returns created event on success`() {
        whenever(validator.validate(any<ManuallyProcessedEventAction>())).thenReturn(emptySet())
        whenever(manualEventCreationService.addManuallyProcessedEvent(any())).thenReturn(Event())

        mockMvc
            .perform(
                post("/api/intern/instance-flow-tracking/events/instance-manually-processed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validAction()))
                    .principal(authentication),
            ).andExpect(status().isOk)
    }

    @Test
    fun `manually processed returns 422 with formatted message on validation error`() {
        whenever(validator.validate(any<ManuallyProcessedEventAction>()))
            .thenReturn(setOf(mock<ConstraintViolation<ManuallyProcessedEventAction>>()))
        whenever(
            validationErrorsFormattingService.format(any<Set<ConstraintViolation<ManuallyProcessedEventAction>>>()),
        ).thenReturn("Validation error: 'sourceApplicationId must be positive'")

        mockMvc
            .perform(
                post("/api/intern/instance-flow-tracking/events/instance-manually-processed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validAction()))
                    .principal(authentication),
            ).andExpect(status().isUnprocessableEntity)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(422))
            .andExpect(jsonPath("$.detail").value("Validation error: 'sourceApplicationId must be positive'"))
    }

    @Test
    fun `manually processed returns 404 when no previous event exists`() {
        whenever(validator.validate(any<ManuallyProcessedEventAction>())).thenReturn(emptySet())
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
        whenever(validator.validate(any<ManuallyProcessedEventAction>())).thenReturn(emptySet())
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
