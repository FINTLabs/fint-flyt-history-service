package no.novari.flyt.history

import no.novari.flyt.history.support.TestTokens
import no.novari.flyt.history.validation.ValidationErrorsFormattingService
import no.novari.flyt.security.FlytSecurityAutoConfiguration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** The internal client chain of flyt-security-starter against `/api/intern-klient`. Only token decoding is stubbed. */
@WebMvcTest(ArchiveInstanceIdInternalClientController::class)
@ImportAutoConfiguration(FlytSecurityAutoConfiguration::class)
@TestPropertySource(
    properties = ["fint.org-id=fintlabs.no", "fint.flyt.authorization.sso.client-id=test-internal-client"],
)
class ArchiveInstanceIdInternalClientSecurityTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    lateinit var eventService: EventService

    @MockitoBean
    lateinit var validationErrorsFormattingService: ValidationErrorsFormattingService

    @BeforeEach
    fun stubTokens() = TestTokens.stub(jwtDecoder)

    @Test
    fun `rejects request without token`() {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `rejects token from another client`() {
        mockMvc
            .perform(
                get(
                    PATH,
                ).header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer ${TestTokens.client("fint-flyt-some-other-oauth2-client")}",
                ),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `rejects a user token`() {
        mockMvc
            .perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer ${TestTokens.user()}"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `accepts token from internal client`() {
        whenever(eventService.findLatestArchiveInstanceId(1L, null, "instance-1")).thenReturn("2024/123")

        mockMvc
            .perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer ${TestTokens.client()}"))
            .andExpect(status().isOk)
            .andExpect(content().string("2024/123"))
    }

    @Test
    fun `accepts token from internal client and returns 404 for unknown instance`() {
        whenever(eventService.findLatestArchiveInstanceId(1L, null, "instance-1")).thenReturn(null)

        mockMvc
            .perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer ${TestTokens.client()}"))
            .andExpect(status().isNotFound)
    }
}

private const val PATH =
    "/api/intern-klient/archive-instance-id?sourceApplicationId=1&sourceApplicationInstanceId=instance-1"
