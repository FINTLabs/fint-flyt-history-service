package no.novari.flyt.history

import no.novari.flyt.security.FlytAccess
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/** The user's access to source applications, through [FlytAccess] (one call to authorization-service per request). */
@Service
class AuthorizationService(
    private val flytAccess: FlytAccess,
) {
    fun validateUserIsAuthorizedForSourceApplication(sourceApplicationId: Long) {
        if (!flytAccess.hasSourceApplication(sourceApplicationId)) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You do not have permission to access or modify data that is related to source application " +
                    "with id=$sourceApplicationId",
            )
        }
    }

    fun getUserAuthorizedSourceApplicationIds(sourceApplicationIds: Set<Long>): Set<Long> =
        (sourceApplicationIds intersect flytAccess.sourceApplicationIds()).toSortedSet()
}
