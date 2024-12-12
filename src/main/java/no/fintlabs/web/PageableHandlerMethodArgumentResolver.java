package no.fintlabs.web;

import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PageableHandlerMethodArgumentResolver implements HandlerMethodArgumentResolver {

    private static final Integer DEFAULT_PAGE = 0;
    private static final Integer DEFAULT_SIZE = 10;
    private static final Integer MAX_SIZE = 1000;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Pageable.class.equals(parameter.getParameterType());
    }

    @Override
    public Mono<Object> resolveArgument(MethodParameter methodParameter, BindingContext bindingContext, ServerWebExchange serverWebExchange) {
        MultiValueMap<String, String> queryParams = serverWebExchange.getRequest().getQueryParams();
        Integer page = Optional.ofNullable(queryParams.getFirst("page"))
                .map(Integer::parseInt)
                .orElse(DEFAULT_PAGE);
        Integer size = Optional.ofNullable(queryParams.getFirst("size"))
                .map(Integer::parseInt)
                .map(s -> Math.min(s, MAX_SIZE))
                .orElse(DEFAULT_SIZE);
        Sort sort = getSort(queryParams.get("sort"));

        return Mono.just(PageRequest.of(page, size, sort));
    }

    private Sort getSort(List<String> sortParams) {
        List<Sort.Order> sortOrderList = sortParams.stream()
                .map(this::createOrderFromSortParam)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        return sortOrderList.isEmpty()
                ? Sort.unsorted()
                : Sort.by(sortOrderList);
    }

    private Optional<Sort.Order> createOrderFromSortParam(String sortParam) {
        if (Objects.isNull(sortParam) || sortParam.isBlank()) {
            return Optional.empty();
        }

        String[] parts = sortParam.split(",");

        if (parts.length > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort parameter: " + sortParam);
        }

        if (parts.length == 1) {
            return Optional.of(Sort.Order.by(sortParam));
        }

        String property = parts[0];
        Sort.Direction direction = Sort.Direction.fromString(parts[1]);

        return Optional.of(new Sort.Order(direction, property));
    }

}