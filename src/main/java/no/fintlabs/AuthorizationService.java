package no.fintlabs;

import no.novari.flyt.resourceserver.security.user.UserAuthorizationService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class AuthorizationService {

    private final UserAuthorizationService userAuthorizationService;

    public AuthorizationService(UserAuthorizationService userAuthorizationService) {
        this.userAuthorizationService = userAuthorizationService;
    }

    public void validateUserIsAuthorizedForSourceApplication(Authentication authentication, Long sourceApplicationId) {
        userAuthorizationService.checkIfUserHasAccessToSourceApplication(authentication, sourceApplicationId);
    }

    public Set<Long> getUserAuthorizedSourceApplicationIds(Authentication authentication) {
        return new HashSet<>(userAuthorizationService.getUserAuthorizedSourceApplicationIds(authentication));
    }

    public Set<Long> getIntersectionWithAuthorizedSourceApplicationIds(
            Authentication authentication,
            Collection<Long> sourceApplicationIds
    ) {
        Set<Long> userAuthorizedSourceApplicationIds = getUserAuthorizedSourceApplicationIds(authentication);
        if (userAuthorizedSourceApplicationIds.isEmpty()) {
            return userAuthorizedSourceApplicationIds;
        }
        return Optional.ofNullable(sourceApplicationIds)
                .map(sourceApplicationInstanceIdsActual ->
                        intersect(
                                userAuthorizedSourceApplicationIds,
                                sourceApplicationInstanceIdsActual
                        )
                )
                .orElse(userAuthorizedSourceApplicationIds);
    }

    private Set<Long> intersect(Collection<Long> a, Collection<Long> b) {
        Set<Long> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        return intersection;
    }

}
