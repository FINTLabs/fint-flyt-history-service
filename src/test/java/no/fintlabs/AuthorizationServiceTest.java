package no.fintlabs;

import no.novari.flyt.resourceserver.security.user.UserAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthorizationServiceTest {

    AuthorizationService authorizationService;
    UserAuthorizationService userAuthorizationService;

    @BeforeEach
    public void setup() {
        userAuthorizationService = mock(UserAuthorizationService.class);
        authorizationService = new AuthorizationService(userAuthorizationService);
    }

    @Test
    public void givenAuthenticationWithNoAuthorizedSourceApplications_whenGetIntersectionWithAuthorizedSourceApplicationIds_shouldReturnEmptyList() {
        Authentication authentication = mockAuthorizedSourceApplicationIds();
        Set<Long> result = authorizationService.getIntersectionWithAuthorizedSourceApplicationIds(
                authentication,
                List.of(1L)
        );
        assertThat(result).isEmpty();
    }

    @Test
    public void givenAuthenticationWithAuthorizedSourceApplicationsAndNotIntersectingSourceApplicationIds_whenGetIntersectionWithAuthorizedSourceApplicationIds_shouldReturnEmptyList() {
        Authentication authentication = mockAuthorizedSourceApplicationIds(2L);
        Set<Long> result = authorizationService.getIntersectionWithAuthorizedSourceApplicationIds(
                authentication,
                List.of(1L)
        );
        assertThat(result).isEmpty();
    }

    @Test
    public void givenAuthenticationWithAuthorizedSourceApplicationsAndTheSameSourceApplicationIds_whenGetIntersectionWithAuthorizedSourceApplicationIds_shouldReturnIntersection() {
        Authentication authentication = mockAuthorizedSourceApplicationIds(1L, 2L);
        Set<Long> result = authorizationService.getIntersectionWithAuthorizedSourceApplicationIds(
                authentication,
                List.of(1L, 3L)
        );
        assertThat(result).containsExactly(1L);
    }

    @Test
    public void givenAuthenticationWithAuthorizedSourceApplicationsAndNullFilter_whenGetIntersectionWithAuthorizedSourceApplicationIds_shouldReturnFilterWithAuthorizedSourceApplications() {
        Authentication authentication = mockAuthorizedSourceApplicationIds(1L, 2L);
        Set<Long> result = authorizationService.getIntersectionWithAuthorizedSourceApplicationIds(
                authentication,
                null
        );
        assertThat(result).containsExactly(1L, 2L);
    }

    private Authentication mockAuthorizedSourceApplicationIds(Long... sourceApplicationIds) {
        String sourceApplicationsClaimAsString = Arrays.stream(sourceApplicationIds)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        Authentication authentication = mock(Authentication.class);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("sourceApplicationIds")).thenReturn(sourceApplicationsClaimAsString);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(userAuthorizationService.getUserAuthorizedSourceApplicationIds(authentication))
                .thenReturn(Set.of(sourceApplicationIds));
        return authentication;
    }

}
