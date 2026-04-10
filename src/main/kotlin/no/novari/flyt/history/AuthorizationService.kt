package no.novari.flyt.history

import no.novari.flyt.webresourceserver.security.user.UserAuthorizationService
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service

@Service
class AuthorizationService(
    private val userAuthorizationService: UserAuthorizationService,
) {
    fun validateUserIsAuthorizedForSourceApplication(
        authentication: Authentication,
        sourceApplicationId: Long,
    ) {
        userAuthorizationService.checkIfUserHasAccessToSourceApplication(authentication, sourceApplicationId)
    }

    fun getUserAuthorizedSourceApplicationIds(authentication: Authentication): Set<Long> {
        return userAuthorizationService.getUserAuthorizedSourceApplicationIds(authentication).toSortedSet()
    }

    fun getIntersectionWithAuthorizedSourceApplicationIds(
        authentication: Authentication,
        sourceApplicationIds: Collection<Long>?,
    ): Set<Long> {
        val userAuthorizedSourceApplicationIds = getUserAuthorizedSourceApplicationIds(authentication)
        if (userAuthorizedSourceApplicationIds.isEmpty()) {
            return userAuthorizedSourceApplicationIds
        }

        return sourceApplicationIds?.let { userAuthorizedSourceApplicationIds intersect it.toSet() }
            ?: userAuthorizedSourceApplicationIds
    }
}
