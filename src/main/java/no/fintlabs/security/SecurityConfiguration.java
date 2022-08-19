package no.fintlabs.security;

import no.vigoiks.resourceserver.security.FintJwtUserConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@EnableWebFluxSecurity
public class SecurityConfiguration {

    @Value("${fint.flyt.security.api.internal.authorized-org-id}")
    private String internalApiAuthorizedOrgId;

    @Bean
    SecurityWebFilterChain internalAccessFilterChain(
            ServerHttpSecurity http,
            @Value("${fint.security.resourceserver.enabled:true}") boolean enabled
    ) {
        return enabled
                ? createSecuredFilterChain(http)
                : createPermitAllFilterChain(http);
    }

    private SecurityWebFilterChain createSecuredFilterChain(ServerHttpSecurity http) {
        http
                .authorizeExchange()
                .pathMatchers("/api/intern/**")
                .hasAnyAuthority("ORGID_" + internalApiAuthorizedOrgId, "ORGID_vigo.no");

        http
                .authorizeExchange()
                .pathMatchers("/**")
                .denyAll();

        return http
                .addFilterBefore(new AuthorizationLogFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
                .oauth2ResourceServer((resourceServer) -> resourceServer
                        .jwt()
                        .jwtAuthenticationConverter(new FintJwtUserConverter())
                )
                .build();
    }

    private SecurityWebFilterChain createPermitAllFilterChain(ServerHttpSecurity http) {
        return http
                .authorizeExchange()
                .anyExchange()
                .permitAll()
                .and()
                .build();
    }

}
