package no.novari.flyt.history

import no.novari.flyt.history.validation.ValidationErrorsFormattingService
import no.novari.flyt.webresourceserver.security.AuthorityMappingService
import no.novari.flyt.webresourceserver.security.AuthorizationServiceConfiguration
import no.novari.flyt.webresourceserver.security.InternalClientApiConfiguration
import no.novari.flyt.webresourceserver.security.InternalUserApiConfiguration
import no.novari.flyt.webresourceserver.security.SecurityConfiguration
import no.novari.flyt.webresourceserver.security.SecurityFilterChainFactoryService
import no.novari.flyt.webresourceserver.security.client.sourceapplication.SourceApplicationJwtConverter
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

/**
 * Runs the security filter chain from flyt-web-resource-server, configured by
 * application-flyt-web-resource-server.yaml, against `/api/intern-klient`.
 * Only token decoding is mocked; the subject of the decoded token is the OAuth client id.
 */
@WebMvcTest(ArchiveInstanceIdInternalClientController::class)
@ImportAutoConfiguration(
    RestClientAutoConfiguration::class,
    AuthorizationServiceConfiguration::class,
    InternalClientApiConfiguration::class,
    InternalUserApiConfiguration::class,
    SecurityConfiguration::class,
)
// Library components that @WebMvcTest filters out of the no.novari component scan.
@Import(AuthorityMappingService::class, SecurityFilterChainFactoryService::class)
@ActiveProfiles("flyt-web-resource-server")
@TestPropertySource(
    properties = [
        "fint.sso.client-id=$HISTORY_CLIENT_ID",
        "fint.flyt.authorization.sso.client-id=test-authorization-client",
        "fint.flyt.authorization.sso.client-secret=test-secret",
    ],
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

    // Picked up by the no.novari component scan; its dependencies need Kafka.
    @MockitoBean
    lateinit var sourceApplicationJwtConverter: SourceApplicationJwtConverter

    @Test
    fun `rejects request without token`() {
        mockMvc
            .perform(get(PATH))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `rejects token from another client`() {
        givenTokenForClient("fint-flyt-some-other-oauth2-client")

        mockMvc
            .perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer token"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `accepts token from history client`() {
        givenTokenForClient(HISTORY_CLIENT_ID)
        whenever(eventService.findLatestArchiveInstanceId(1L, null, "instance-1")).thenReturn("2024/123")

        mockMvc
            .perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer token"))
            .andExpect(status().isOk)
            .andExpect(content().string("2024/123"))
    }

    @Test
    fun `accepts token from history client and returns 404 for unknown instance`() {
        givenTokenForClient(HISTORY_CLIENT_ID)
        whenever(eventService.findLatestArchiveInstanceId(1L, null, "instance-1")).thenReturn(null)

        mockMvc
            .perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer token"))
            .andExpect(status().isNotFound)
    }

    private fun givenTokenForClient(clientId: String) {
        whenever(jwtDecoder.decode(any())).thenReturn(
            Jwt
                .withTokenValue("token")
                .header("alg", "none")
                .subject(clientId)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build(),
        )
    }
}

private const val HISTORY_CLIENT_ID = "test-history-client"
private const val PATH =
    "/api/intern-klient/archive-instance-id?sourceApplicationId=1&sourceApplicationInstanceId=instance-1"
