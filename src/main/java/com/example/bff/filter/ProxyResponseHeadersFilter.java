package com.example.bff.filter;

import java.util.Optional;
import java.util.function.BiFunction;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
public class ProxyResponseHeadersFilter {

    public BiFunction<ServerRequest, ServerResponse, ServerResponse> asAfterFunction(
            String routeId) {
        return (request, response) -> {
            String correlationId =
                    Optional.ofNullable(
                                    request.headers()
                                            .firstHeader(
                                                    InternalJwtRelayFilter.CORRELATION_ID_HEADER))
                            .orElse("n/a");
            String appUser =
                    Optional.ofNullable(
                                    request.headers()
                                            .firstHeader(InternalJwtRelayFilter.APP_USER_HEADER))
                            .orElse("anonymous");

            response.headers().set(InternalJwtRelayFilter.CORRELATION_ID_HEADER, correlationId);
            response.headers().set(InternalJwtRelayFilter.APP_USER_HEADER, appUser);
            response.headers().set("X-Gateway-Route", routeId);
            return response;
        };
    }
}
