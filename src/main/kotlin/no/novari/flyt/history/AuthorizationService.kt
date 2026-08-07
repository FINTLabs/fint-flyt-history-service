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

    fun getUserAuthorizedSourceApplicationIds(
        authentication: Authentication,
        sourceApplicationIds: Set<Long>,
    ): Set<Long> {
        return userAuthorizationService
            .getUserAuthorizedSourceApplicationIds(authentication, sourceApplicationIds)
            .toSortedSet()
    }
}
