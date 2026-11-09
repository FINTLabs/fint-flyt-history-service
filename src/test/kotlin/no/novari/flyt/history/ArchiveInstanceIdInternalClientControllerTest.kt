package no.novari.flyt.history

import no.novari.flyt.history.validation.ValidationErrorsFormattingService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ArchiveInstanceIdInternalClientControllerTest {
    private val eventService: EventService = mock()
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(ArchiveInstanceIdInternalClientController(eventService))
                .setControllerAdvice(GlobalExceptionHandler(mock<ValidationErrorsFormattingService>()))
                .build()
    }

    @Test
    fun `returns latest archive instance id`() {
        whenever(eventService.findLatestArchiveInstanceId(1L, null, "instance-1")).thenReturn("2024/123")

        mockMvc
            .perform(get("$PATH?sourceApplicationId=1&sourceApplicationInstanceId=instance-1"))
            .andExpect(status().isOk)
            .andExpect(content().string("2024/123"))
    }

    @Test
    fun `passes sourceApplicationIntegrationId when given`() {
        whenever(eventService.findLatestArchiveInstanceId(1L, "integration-1", "instance-1")).thenReturn("2024/123")

        mockMvc
            .perform(
                get(
                    "$PATH?sourceApplicationId=1&sourceApplicationIntegrationId=integration-1" +
                        "&sourceApplicationInstanceId=instance-1",
                ),
            ).andExpect(status().isOk)
            .andExpect(content().string("2024/123"))
    }

    @Test
    fun `returns 404 for unknown instance`() {
        mockMvc
            .perform(get("$PATH?sourceApplicationId=1&sourceApplicationInstanceId=unknown"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `returns 400 when sourceApplicationInstanceId is missing`() {
        mockMvc
            .perform(get("$PATH?sourceApplicationId=1"))
            .andExpect(status().isBadRequest)
    }

    private companion object {
        const val PATH = "/api/intern-klient/archive-instance-id"
    }
}
