package no.novari.flyt.instance

import jakarta.persistence.EntityNotFoundException
import no.novari.flyt.history.GlobalExceptionHandler
import no.novari.flyt.history.support.TestTokens
import no.novari.flyt.history.validation.ValidationErrorsFormattingService
import no.novari.flyt.security.FlytSecurityAutoConfiguration
import no.novari.flyt.security.UserRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.kafka.KafkaException
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException

@WebMvcTest(properties = ["novari.flyt.instance-functionality.enabled=true"])
@ContextConfiguration(classes = [InstanceRetryController::class])
@ImportAutoConfiguration(FlytSecurityAutoConfiguration::class)
@TestPropertySource(
    properties = [TestTokens.ORG_PROPERTY, TestTokens.INTERNAL_CLIENT_PROPERTY, TestTokens.USER_AUDIENCE_PROPERTY],
)
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
        mvc.perform(request(batch).header("Authorization", bearer)).andExpect(status().isForbidden)
        verifyNoInteractions(service)
    }

    @Test
    fun `authorized single retry returns an empty success response`() {
        token()
        mvc
            .perform(request(false).header("Authorization", bearer))
            .andExpect(status().isOk)
            .andExpect(content().string(""))
        verify(service).retry(123L)
    }

    @Test
    fun `missing instance gives 404 and missing headers gives 500`() {
        token()
        doThrow(EntityNotFoundException()).whenever(service).retry(123L)
        mvc.perform(request(false).header("Authorization", bearer)).andExpect(status().isNotFound)
        doThrow(MissingRetryHeadersException(123L)).whenever(service).retry(123L)
        mvc.perform(request(false).header("Authorization", bearer)).andExpect(status().isInternalServerError)
    }

    @Test
    fun `single retry with an invalid status returns a conflict with an explanation`() {
        token()
        val conflict = RetryStatusConflictException(123L)
        doThrow(conflict).whenever(service).retry(123L)
        mvc
            .perform(request(false).header("Authorization", bearer))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.detail").value(conflict.message))
    }

    @Test
    fun `a source application the user does not have gives 403`() {
        token()
        doThrow(ResponseStatusException(HttpStatus.FORBIDDEN)).whenever(service).retry(123L)
        mvc.perform(request(false).header("Authorization", bearer)).andExpect(status().isForbidden)
    }

    @Test
    fun `synchronous publication failure gives 500`() {
        token()
        doThrow(KafkaException("Publication failed")).whenever(service).retry(123L)
        mvc.perform(request(false).header("Authorization", bearer)).andExpect(status().isInternalServerError)
    }

    @Test
    fun `malformed batch retains the legacy 400 response instead of a history problem detail`() {
        token()
        mvc
            .perform(
                post(
                    BATCH,
                ).contentType(MediaType.APPLICATION_JSON).content("[").header("Authorization", bearer),
            ).andExpect(status().isBadRequest)
            .andExpect(content().string(""))
        verifyNoInteractions(service)
    }

    @Test
    fun `batch attempts each ID once in order and continues after failures`() {
        token()
        doThrow(RetryStatusConflictException(123L)).whenever(service).retry(123L)
        doThrow(KafkaException("Publication failed")).whenever(service).retry(124L)
        mvc
            .perform(request(true).content("[123,124,125,125]").header("Authorization", bearer))
            .andExpect(status().isOk)
            .andExpect(content().string(""))
        org.mockito.kotlin.inOrder(service) {
            verify(service).retry(123L)
            verify(service).retry(124L)
            verify(service).retry(125L)
        }
        verifyNoMoreInteractions(service)
    }

    @Test
    fun `batch skips an instance from a source application the user does not have and retries the rest`() {
        token()
        doThrow(ResponseStatusException(HttpStatus.FORBIDDEN)).whenever(service).retry(124L)
        mvc
            .perform(request(true).content("[123,124,125]").header("Authorization", bearer))
            .andExpect(status().isOk)
            .andExpect(content().string(""))
        org.mockito.kotlin.inOrder(service) {
            verify(service).retry(123L)
            verify(service).retry(124L)
            verify(service).retry(125L)
        }
        verifyNoMoreInteractions(service)
    }

    @Test
    fun `empty batch performs no retries`() {
        token()
        mvc
            .perform(
                post(
                    BATCH,
                ).contentType(MediaType.APPLICATION_JSON).content("[]").header("Authorization", bearer),
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
                .perform(request(true).header("Authorization", bearer))
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
                    .header("Authorization", bearer),
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

    private var bearer = ""

    @BeforeEach
    fun stubTokens() = TestTokens.stub(decoder)

    private fun token(authorized: Boolean = true) {
        bearer = "Bearer ${TestTokens.user(role = if (authorized) UserRole.USER else null)}"
    }

    companion object {
        private const val BATCH = "/api/intern/handlinger/instanser/prov-igjen/batch"
    }
}
