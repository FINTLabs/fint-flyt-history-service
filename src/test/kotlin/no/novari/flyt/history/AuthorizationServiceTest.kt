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
    fun `given authentication with no authorized source applications when get intersection then return empty list`() {
        val authentication = mockAuthorizedSourceApplicationIds()

        val result =
            authorizationService.getIntersectionWithAuthorizedSourceApplicationIds(
                authentication,
                listOf(1L),
            )

        assertThat(result).isEmpty()
    }

    @Test
    fun `given authorized source applications and no intersecting ids then return empty list`() {
        val authentication = mockAuthorizedSourceApplicationIds(2L)

        val result =
            authorizationService.getIntersectionWithAuthorizedSourceApplicationIds(
                authentication,
                listOf(1L),
            )

        assertThat(result).isEmpty()
    }

    @Test
    fun `given authentication with same source application ids when get intersection then return intersection`() {
        val authentication = mockAuthorizedSourceApplicationIds(1L, 2L)

        val result =
            authorizationService.getIntersectionWithAuthorizedSourceApplicationIds(
                authentication,
                listOf(1L, 3L),
            )

        assertThat(result).containsExactly(1L)
    }

    @Test
    fun `given authorized source applications and null filter then return authorized source applications`() {
        val authentication = mockAuthorizedSourceApplicationIds(1L, 2L)

        val result =
            authorizationService.getIntersectionWithAuthorizedSourceApplicationIds(
                authentication,
                null,
            )

        assertThat(result).containsExactly(1L, 2L)
    }

    private fun mockAuthorizedSourceApplicationIds(vararg sourceApplicationIds: Long): Authentication {
        val authentication: Authentication = mock()
        whenever(userAuthorizationService.getUserAuthorizedSourceApplicationIds(authentication))
            .thenReturn(setOf(*sourceApplicationIds.toTypedArray()))
        return authentication
    }
}
