package no.fintlabs;

import no.fintlabs.model.SourceApplicationIdFilter;
import no.fintlabs.resourceserver.security.user.UserAuthorizationUtil;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class AuthorizationService {

    public void validateUserIsAuthorizedForSourceApplication(Authentication authentication, Long sourceApplicationId) {
        UserAuthorizationUtil.checkIfUserHasAccessToSourceApplication(authentication, sourceApplicationId);
    }

    public Set<Long> getUserAuthorizedSourceApplicationIds(Authentication authentication) {
        return new HashSet<>(UserAuthorizationUtil.convertSourceApplicationIdsStringToList(authentication));
    }

    public <T extends SourceApplicationIdFilter<T>> T
    createNewFilterLimitedByUserAuthorizedSourceApplicationIds(
            Authentication authentication,
            SourceApplicationIdFilter<T> sourceApplicationIdFilter
    ) {
        return sourceApplicationIdFilter
                .toBuilder()
                .sourceApplicationId(
                        getUserAuthorizedFilterSourceApplicationIds(
                                authentication,
                                sourceApplicationIdFilter.getSourceApplicationIds()
                        )
                )
                .build();
    }

    private Set<Long> getUserAuthorizedFilterSourceApplicationIds(
            Authentication authentication,
            Collection<Long> filteredSourceApplicationIds
    ) {
        Set<Long> userAuthorizedSourceApplicationsIds = getUserAuthorizedSourceApplicationIds(authentication);
        return Optional.ofNullable(filteredSourceApplicationIds).map(
                filterSourceApplicationIds -> intersectAuthorizationAndFilterSourceApplicationIds(
                        userAuthorizedSourceApplicationsIds,
                        filterSourceApplicationIds
                )
        ).orElse(userAuthorizedSourceApplicationsIds);
    }

    private Set<Long> intersectAuthorizationAndFilterSourceApplicationIds(
            Collection<Long> userAuthorizationSourceApplicationIds,
            Collection<Long> filterSourceApplicationIds
    ) {
        if (filterSourceApplicationIds == null) {
            return new HashSet<>(userAuthorizationSourceApplicationIds);
        }

        Set<Long> intersection = new HashSet<>(userAuthorizationSourceApplicationIds);
        intersection.retainAll(filterSourceApplicationIds);
        return intersection;
    }

}
