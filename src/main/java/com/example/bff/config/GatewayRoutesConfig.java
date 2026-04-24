package com.example.bff.config;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.stripPrefix;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

import com.example.bff.filter.GatewayMvcRateLimit;
import com.example.bff.filter.InternalJwtRelayFilter;
import com.example.bff.filter.ProxyResponseHeadersFilter;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GatewayRoutesConfig {

    final InternalJwtRelayFilter internalJwtRelayFilter;
    final ProxyResponseHeadersFilter proxyResponseHeadersFilter;
    final GatewayMvcRateLimit gatewayMvcRateLimit;

    @Value("${app.downstream.users}")
    String usersServiceUri;

    @Value("${app.downstream.orders}")
    String ordersServiceUri;

    @Bean
    RouterFunction<?> gatewayRoutes() {
        RouterFunction<?> usersRoute =
                route("users-service-route")
                        .route(RequestPredicates.path("/proxy/users/**"), http())
                        .before(internalJwtRelayFilter.asBeforeFunction())
                        .before(stripPrefix(1))
                        .before(uri(usersServiceUri))
                        .filter(gatewayMvcRateLimit.asFilter())
                        .filter(circuitBreaker("users-service"))
                        .after(proxyResponseHeadersFilter.asAfterFunction("users-service-route"))
                        .build();

        RouterFunction<?> ordersRoute =
                route("orders-service-route")
                        .route(RequestPredicates.path("/proxy/orders/**"), http())
                        .before(internalJwtRelayFilter.asBeforeFunction())
                        .before(stripPrefix(1))
                        .before(uri(ordersServiceUri))
                        .filter(gatewayMvcRateLimit.asFilter())
                        .filter(circuitBreaker("orders-service"))
                        .after(proxyResponseHeadersFilter.asAfterFunction("orders-service-route"))
                        .build();

        return usersRoute.andOther(ordersRoute);
    }
}
