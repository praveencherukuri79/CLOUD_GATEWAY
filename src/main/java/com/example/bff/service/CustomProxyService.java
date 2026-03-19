package com.example.bff.service;

import com.example.bff.exception.ProxyRequestException;
import com.example.bff.util.CustomProxyUtils;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CustomProxyService {

    static final String CUSTOM_ROUTE_HEADER = "X-Custom-Route";

    InternalJwtService internalJwtService;
    RestClient restClient = RestClient.create();

    public ResponseEntity<byte[]> forward(
            HttpServletRequest request,
            Authentication authentication,
            byte[] body,
            String routePrefix,
            String targetBaseUri,
            String routeId) {
        String requestPath = request.getRequestURI();
        String correlationId = CustomProxyUtils.resolveCorrelationId(request);
        validateAuthentication(authentication, requestPath, correlationId);

        String targetUri = CustomProxyUtils.buildTargetUri(request, routePrefix, targetBaseUri);
        String appUser = authentication.getName();
        String token = internalJwtService.createToken(authentication);
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        HttpHeaders outboundHeaders =
                CustomProxyUtils.buildOutboundHeaders(request, appUser, correlationId, token);

        try {
            ResponseEntity<byte[]> downstreamResponse =
                    executeDownstreamCall(method, targetUri, outboundHeaders, body);
            return buildSuccessResponse(downstreamResponse, correlationId, appUser, routeId);
        } catch (RestClientResponseException ex) {
            log.warn(
                    "Custom proxy downstream returned status={} routeId={} correlationId={} targetUri={} user={}",
                    ex.getStatusCode().value(),
                    routeId,
                    correlationId,
                    targetUri,
                    appUser);

            return buildErrorResponse(ex, correlationId, appUser, routeId);
        } catch (ResourceAccessException ex) {
            log.error(
                    "Custom proxy target unavailable routeId={} correlationId={} targetUri={} user={}",
                    routeId,
                    correlationId,
                    targetUri,
                    appUser,
                    ex);
            throw new ProxyRequestException(
                    HttpStatus.BAD_GATEWAY,
                    "Custom proxy target is unavailable for route " + routeId,
                    correlationId);
        }
    }

    private void validateAuthentication(
            Authentication authentication, String path, String correlationId) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            log.warn(
                    "No authenticated user found for custom proxy path={} correlationId={}",
                    path,
                    correlationId);
            throw new ProxyRequestException(
                    HttpStatus.UNAUTHORIZED,
                    "No authenticated user found in SecurityContext",
                    correlationId);
        }
    }

    private ResponseEntity<byte[]> executeDownstreamCall(
            HttpMethod method, String targetUri, HttpHeaders outboundHeaders, byte[] body) {
        RestClient.RequestBodySpec requestSpec =
                restClient
                        .method(method)
                        .uri(URI.create(targetUri))
                        .headers(headers -> headers.addAll(outboundHeaders));

        if (CustomProxyUtils.hasBody(body)) {
            requestSpec.body(body);
        }

        return requestSpec.retrieve().toEntity(byte[].class);
    }

    private ResponseEntity<byte[]> buildSuccessResponse(
            ResponseEntity<byte[]> downstreamResponse,
            String correlationId,
            String appUser,
            String routeId) {
        HttpHeaders responseHeaders =
                CustomProxyUtils.buildProxyResponseHeaders(
                        downstreamResponse.getHeaders(),
                        correlationId,
                        appUser,
                        CUSTOM_ROUTE_HEADER, routeId);

        return new ResponseEntity<>(
                downstreamResponse.getBody(), responseHeaders, downstreamResponse.getStatusCode());
    }

    private ResponseEntity<byte[]> buildErrorResponse(
            RestClientResponseException ex, String correlationId, String appUser, String routeId) {
        HttpHeaders responseHeaders =
                CustomProxyUtils.buildProxyResponseHeaders(
                        ex.getResponseHeaders(),
                        correlationId,
                        appUser,
                        CUSTOM_ROUTE_HEADER,
                        routeId);

        return new ResponseEntity<>(
                ex.getResponseBodyAsByteArray(), responseHeaders, ex.getStatusCode());
    }
}
