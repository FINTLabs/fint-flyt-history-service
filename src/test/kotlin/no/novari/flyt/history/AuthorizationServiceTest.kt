package no.novari.flyt.history

import no.novari.flyt.webresourceserver.security.user.UserAuthorizationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.core.Authentication

class AuthorizationServiceTest {
    private lateinit var authorizationService: AuthorizationService
    private lateinit var userAuthorizationService: UserAuthorizationService

    @BeforeEach
    fun setup() {
        userAuthorizationService = mock()
        authorizationService = AuthorizationService(userAuthorizationService)
    }

    @Test
    fun `returns the source applications authorized by the web resource server`() {
        val authentication: Authentication = mock()
        val candidateIds = setOf(3L, 1L, 2L)
        whenever(userAuthorizationService.getUserAuthorizedSourceApplicationIds(authentication, candidateIds))
            .thenReturn(setOf(2L, 1L))

        val result = authorizationService.getUserAuthorizedSourceApplicationIds(authentication, candidateIds)

        assertThat(result).containsExactly(1L, 2L)
    }
}
