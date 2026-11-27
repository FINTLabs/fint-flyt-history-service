package no.novari.flyt.history

import no.novari.flyt.security.FlytAccess
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.web.server.ResponseStatusException

class AuthorizationServiceTest {
    private val flytAccess: FlytAccess =
        mock {
            on { sourceApplicationIds() } doReturn setOf(2L, 1L, 5L)
            on { hasSourceApplication(1L) } doReturn true
        }
    private val authorizationService = AuthorizationService(flytAccess)

    @Test
    fun `returns the requested source applications the user has, sorted`() {
        assertThat(
            authorizationService.getUserAuthorizedSourceApplicationIds(setOf(3L, 1L, 2L)),
        ).containsExactly(1L, 2L)
    }

    @Test
    fun `a source application the user does not have gives 403`() {
        authorizationService.validateUserIsAuthorizedForSourceApplication(1L)
        assertThatThrownBy { authorizationService.validateUserIsAuthorizedForSourceApplication(3L) }
            .isInstanceOfSatisfying(
                ResponseStatusException::class.java,
            ) { assertThat(it.statusCode.value()).isEqualTo(403) }
    }
}
