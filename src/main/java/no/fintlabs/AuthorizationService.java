package no.fintlabs;

import no.fintlabs.model.SourceApplicationIdFilter;
import no.fintlabs.resourceserver.security.user.UserAuthorizationUtil;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

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
                                sourceApplicationIdFilter::getSourceApplicationIds
                        )
                )
                .build();
    }

    private Set<Long> getUserAuthorizedFilterSourceApplicationIds(
            Authentication authentication,
            Supplier<Optional<Collection<Long>>> filteredSourceApplicationIdsSupplier
    ) {
        Set<Long> userAuthorizedSourceApplicationsIds = getUserAuthorizedSourceApplicationIds(authentication);
        return filteredSourceApplicationIdsSupplier.get().map(
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
