package no.fintlabs.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
public class AuthorizationLogFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        log.trace(String.valueOf(exchange.getRequest().getHeaders().get("Authorization")));

        return chain.filter(exchange);
    }
}