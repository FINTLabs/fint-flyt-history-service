package no.novari.flyt.history

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.validation.Validation
import no.novari.flyt.history.model.event.Event
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.event.EventType
import no.novari.flyt.history.validation.ValidationErrorsFormattingService
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.security.core.Authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.util.UUID
import java.util.stream.Stream

class ManualEventCommentWebMvcTest {
    private val eventService: EventService = mock()
    private val authorizationService: AuthorizationService = mock()
    private val authentication: Authentication = mock()
    private val uuidService: UuidService = mock()
    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()
    private val validatorFactory = Validation.buildDefaultValidatorFactory()
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val formattingService = ValidationErrorsFormattingService()
        val controller =
            HistoryController(
                authorizationService,
                eventService,
                ManualEventCreationService(Clock.systemUTC(), uuidService, "history-service", eventService),
                validatorFactory,
                formattingService,
            )
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(GlobalExceptionHandler(formattingService))
                .setMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
                .defaultResponseCharacterEncoding<StandaloneMockMvcBuilder>(Charsets.UTF_8)
                .build()
    }

    @AfterEach
    fun tearDown() {
        validatorFactory.close()
    }

    @ParameterizedTest
    @MethodSource("validRequests")
    fun `all manual statuses accept optional comments and return normalized values`(
        category: EventCategory,
        body: Map<String, Any?>,
        expectedComment: String?,
    ) {
        whenever(eventService.findLatestStatusEventBySourceApplicationAggregateInstanceId(any()))
            .thenReturn(errorEvent())
        whenever(uuidService.generateUuid()).thenReturn(UUID.randomUUID())
        whenever(eventService.save(any())).thenAnswer { it.getArgument<Event>(0) }

        val result =
            mockMvc
                .perform(request(category, body))
                .andExpect(status().isOk)
                .andReturn()
        val response = objectMapper.readTree(result.response.contentAsString)
        assertThat(response.has("comment")).isTrue()
        assertThat(response["comment"].takeUnless { it.isNull }?.asText()).isEqualTo(expectedComment)

        val captor = argumentCaptor<Event>()
        verify(eventService).save(captor.capture())
        assertThat(captor.firstValue.comment).isEqualTo(expectedComment)
        assertThat(captor.firstValue.category).isEqualTo(category)
        verify(authorizationService).validateUserIsAuthorizedForSourceApplication(authentication, 1L)
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
    fun `comments over 250 characters return existing 422 validation response`(category: EventCategory) {
        mockMvc
            .perform(request(category, body(category) + ("comment" to "a".repeat(251))))
            .andExpect(status().isUnprocessableEntity)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(422))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("comment")))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("250")))

        verifyNoInteractions(eventService, authorizationService)
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
    fun `comments do not bypass authorization`(category: EventCategory) {
        whenever(authorizationService.validateUserIsAuthorizedForSourceApplication(authentication, 1L))
            .thenThrow(ResponseStatusException(HttpStatus.FORBIDDEN, "No access"))

        mockMvc
            .perform(request(category, body(category) + ("comment" to "Kommentar")))
            .andExpect(status().isForbidden)

        verifyNoInteractions(eventService)
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
    fun `comments do not bypass status transition rules`(category: EventCategory) {
        whenever(eventService.findLatestStatusEventBySourceApplicationAggregateInstanceId(any()))
            .thenReturn(Event(type = EventType.INFO))

        mockMvc
            .perform(request(category, body(category) + ("comment" to "Kommentar")))
            .andExpect(status().isBadRequest)

        verifyNoInteractions(uuidService)
    }

    private fun request(
        category: EventCategory,
        body: Map<String, Any?>,
    ) = post("/api/intern/instance-flow-tracking/events/${category.eventName}")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body))
        .principal(authentication)

    private fun errorEvent() =
        Event(
            type = EventType.ERROR,
            instanceFlowHeaders =
                InstanceFlowHeaders
                    .builder()
                    .sourceApplicationId(1L)
                    .sourceApplicationIntegrationId("integration-1")
                    .sourceApplicationInstanceId("instance-1")
                    .correlationId(UUID.randomUUID())
                    .integrationId(10L)
                    .build(),
            comment = "Kommentar på en tidligere hendelse",
        )

    companion object {
        private fun body(category: EventCategory): Map<String, Any?> {
            val body =
                mutableMapOf<String, Any?>(
                    "sourceApplicationId" to 1L,
                    "sourceApplicationIntegrationId" to "integration-1",
                    "sourceApplicationInstanceId" to "instance-1",
                )
            if (category == EventCategory.INSTANCE_MANUALLY_PROCESSED) {
                body["archiveInstanceId"] = "archive-1"
            }
            return body
        }

        @JvmStatic
        fun validRequests(): Stream<Arguments> =
            listOf(
                EventCategory.INSTANCE_MANUALLY_PROCESSED,
                EventCategory.INSTANCE_MANUALLY_REJECTED,
                EventCategory.INSTANCE_STATUS_OVERRIDDEN_AS_TRANSFERRED,
            ).flatMap { category ->
                val comments =
                    listOf(
                        null,
                        "",
                        " \t\n",
                        "Manuelt behandlet: æøå",
                        "  Behold mellomrom  ",
                        "a".repeat(250),
                        "<b>Ren tekst</b>",
                    )
                listOf(Arguments.of(category, body(category), null)) +
                    comments.map { comment ->
                        Arguments.of(
                            category,
                            body(category) + ("comment" to comment),
                            comment?.takeUnless(String::isBlank),
                        )
                    }
            }.stream()
    }
}
