package no.novari.flyt.instance

import jakarta.persistence.EntityNotFoundException
import no.novari.flyt.history.GlobalExceptionHandler
import no.novari.flyt.history.validation.ValidationErrorsFormattingService
import no.novari.flyt.webresourceserver.security.AuthorityMappingService
import no.novari.flyt.webresourceserver.security.InternalUserApiConfiguration
import no.novari.flyt.webresourceserver.security.SecurityConfiguration
import no.novari.flyt.webresourceserver.security.SecurityFilterChainFactoryService
import no.novari.flyt.webresourceserver.security.properties.InternalApiSecurityProperties
import no.novari.flyt.webresourceserver.security.user.UserRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.kafka.KafkaException
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(properties = ["novari.flyt.instance-functionality.enabled=true"])
@ContextConfiguration(classes = [InstanceRetryController::class, InstanceRetryControllerWebMvcTest.Security::class])
@Import(GlobalExceptionHandler::class)
class InstanceRetryControllerWebMvcTest {
    @Autowired
    lateinit var mvc: MockMvc

    @MockitoBean
    lateinit var service: InstanceRetryService

    @MockitoBean
    lateinit var decoder: JwtDecoder

    @MockitoBean
    lateinit var formatter: ValidationErrorsFormattingService

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `unauthenticated requests cannot invoke either endpoint`(batch: Boolean) {
        mvc.perform(request(batch)).andExpect(status().isUnauthorized)
        verifyNoInteractions(service)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `authenticated callers without the internal user role are forbidden`(batch: Boolean) {
        token(authorized = false)
        mvc.perform(request(batch).header("Authorization", "Bearer token")).andExpect(status().isForbidden)
        verifyNoInteractions(service)
    }

    @Test
    fun `authorized single retry returns an empty success response`() {
        token()
        mvc
            .perform(request(false).header("Authorization", "Bearer token"))
            .andExpect(status().isOk)
            .andExpect(content().string(""))
        verify(service).retry(123L)
    }

    @Test
    fun `missing instance gives 404 and missing headers gives 500`() {
        token()
        doThrow(EntityNotFoundException()).whenever(service).retry(123L)
        mvc.perform(request(false).header("Authorization", "Bearer token")).andExpect(status().isNotFound)
        doThrow(MissingRetryHeadersException(123L)).whenever(service).retry(123L)
        mvc.perform(request(false).header("Authorization", "Bearer token")).andExpect(status().isInternalServerError)
    }

    @Test
    fun `synchronous publication failure gives 500`() {
        token()
        doThrow(KafkaException("Publication failed")).whenever(service).retry(123L)
        mvc.perform(request(false).header("Authorization", "Bearer token")).andExpect(status().isInternalServerError)
    }

    @Test
    fun `batch continues sequentially after failures and returns no body`() {
        token()
        doThrow(EntityNotFoundException()).whenever(service).retry(123L)
        doThrow(KafkaException("Publication failed")).whenever(service).retry(124L)
        mvc
            .perform(request(true).header("Authorization", "Bearer token"))
            .andExpect(status().isOk)
            .andExpect(content().string(""))
        org.mockito.kotlin.inOrder(service) {
            verify(service).retry(123L)
            verify(service).retry(124L)
            verify(service).retry(125L)
        }
    }

    @Test
    fun `empty batch performs no retries`() {
        token()
        mvc
            .perform(
                post(
                    BATCH,
                ).contentType(MediaType.APPLICATION_JSON).content("[]").header("Authorization", "Bearer token"),
            ).andExpect(status().isOk)
            .andExpect(content().string(""))
        verifyNoInteractions(service)
    }

    @Test
    fun `interruption stops the batch and preserves interrupt status`() {
        token()
        doThrow(InterruptedException("interrupted")).whenever(service).retry(123L)
        try {
            mvc
                .perform(request(true).header("Authorization", "Bearer token"))
                .andExpect(status().isInternalServerError)
            assertThat(Thread.currentThread().isInterrupted).isTrue()
        } finally {
            Thread.interrupted()
        }
        verify(service).retry(123L)
        verifyNoMoreInteractions(service)
    }

    @Test
    fun `batch accepts thousands of instances and attempts all IDs in order despite a failed item`() {
        token()
        val instanceIds = (1L..5000L).toList()
        doThrow(KafkaException("Publication failed")).whenever(service).retry(2500L)
        mvc
            .perform(
                post(BATCH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(instanceIds.joinToString(prefix = "[", postfix = "]"))
                    .header("Authorization", "Bearer token"),
            ).andExpect(status().isOk)
            .andExpect(content().string(""))
        val attempted = argumentCaptor<Long>()
        verify(service, times(instanceIds.size)).retry(attempted.capture())
        assertThat(attempted.allValues).containsExactlyElementsOf(instanceIds)
        verifyNoMoreInteractions(service)
    }

    private fun request(batch: Boolean) =
        if (batch) {
            post(BATCH).contentType(MediaType.APPLICATION_JSON).content("[123,124,125]")
        } else {
            post("/api/intern/handlinger/instanser/123/prov-igjen")
        }

    private fun token(authorized: Boolean = true) {
        whenever(decoder.decode("token")).thenReturn(
            Jwt
                .withTokenValue("token")
                .header("alg", "RS256")
                .subject("user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("organizationid", "afk.no")
                .claim("objectidentifier", UUID.randomUUID().toString())
                .claim("roles", if (authorized) listOf(UserRole.USER.claimValue) else emptyList<String>())
                .build(),
        )
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Security {
        @Bean
        fun internalApi(http: HttpSecurity): SecurityFilterChain {
            val properties =
                InternalApiSecurityProperties().apply {
                    authorizedOrgIdRolePairsJson = """{"afk.no":["USER"]}"""
                    parseAndSetAuthorizedOrgIdRolePairs()
                }
            val config = InternalUserApiConfiguration()
            val authorityMapping = config.userRoleAuthorityMappingService(AuthorityMappingService())
            return SecurityConfiguration().internalUserApiFilterChain(
                http,
                config.userJwtConverter(
                    config.userRoleFilteringService(properties),
                    config.userRoleHierarchyService(),
                    authorityMapping,
                ),
                authorityMapping,
                SecurityFilterChainFactoryService(),
            )
        }
    }

    companion object {
        private const val BATCH = "/api/intern/handlinger/instanser/prov-igjen/batch"
    }
}
