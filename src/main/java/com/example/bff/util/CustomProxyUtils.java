package com.example.bff.util;

import com.example.bff.filter.InternalJwtRelayFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

public final class CustomProxyUtils {

    private static final String PROXY_MODE_HEADER = "X-Proxy-Mode";
    private static final String CUSTOM_PROXY_MODE = "custom";
    private static final List<String> EXCLUDED_OUTBOUND_HEADERS =
        List.of(
            HttpHeaders.HOST,
            HttpHeaders.COOKIE,
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.CONTENT_LENGTH);

    private CustomProxyUtils() {}

    public static String resolveCorrelationId(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(InternalJwtRelayFilter.CORRELATION_ID_HEADER))
                .filter(StringUtils::hasText)
                .orElseGet(() -> UUID.randomUUID().toString());
    }

    public static String buildTargetUri(
            HttpServletRequest request, String routePrefix, String targetBaseUri) {
        String requestPath = normalizeRequestPath(request);
        String downstreamPath = resolveDownstreamPath(requestPath, routePrefix);

        String normalizedBaseUri =
                targetBaseUri.endsWith("/")
                        ? targetBaseUri.substring(0, targetBaseUri.length() - 1)
                        : targetBaseUri;
        String normalizedPath = downstreamPath.startsWith("/") ? downstreamPath : "/" + downstreamPath;
        String queryString = request.getQueryString();

        return normalizedBaseUri
                + normalizedPath
                + (StringUtils.hasText(queryString) ? "?" + queryString : "");
    }

    private static String normalizeRequestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();

        if (StringUtils.hasText(contextPath) && requestUri.startsWith(contextPath)) {
            String pathWithoutContext = requestUri.substring(contextPath.length());
            return StringUtils.hasText(pathWithoutContext) ? pathWithoutContext : "/";
        }

        return requestUri;
    }

    private static String resolveDownstreamPath(String requestPath, String routePrefix) {
        if (!requestPath.startsWith(routePrefix)) {
            return requestPath;
        }

        String downstreamPath = requestPath.substring(routePrefix.length());
        return StringUtils.hasText(downstreamPath) ? downstreamPath : "/";
    }

    public static HttpHeaders buildOutboundHeaders(
            HttpServletRequest request, String appUser, String correlationId, String token) {
        HttpHeaders headers = new HttpHeaders();
        List<String> headerNames =
                request.getHeaderNames() == null
                        ? List.of()
                        : Collections.list(request.getHeaderNames());

        for (String headerName : headerNames) {

            if (isExcludedOutboundHeader(headerName)) {
                continue;
            }

            List<String> headerValues = Collections.list(request.getHeaders(headerName));
            for (String headerValue : headerValues) {
                headers.add(headerName, headerValue);
            }
        }

        headers.setBearerAuth(token);
        headers.set(InternalJwtRelayFilter.APP_USER_HEADER, appUser);
        headers.set(InternalJwtRelayFilter.CORRELATION_ID_HEADER, correlationId);
        return headers;
    }

    public static HttpHeaders buildProxyResponseHeaders(
            HttpHeaders source,
            String correlationId,
            String appUser,
            String routeHeader,
            String routeId) {
        HttpHeaders headers = copyHeaders(source);
        headers.set(InternalJwtRelayFilter.CORRELATION_ID_HEADER, correlationId);
        headers.set(InternalJwtRelayFilter.APP_USER_HEADER, appUser);
        headers.set(routeHeader, routeId);
        headers.set(PROXY_MODE_HEADER, CUSTOM_PROXY_MODE);
        return headers;
    }

    public static boolean hasBody(byte[] body) {
        return body != null && body.length > 0;
    }

    private static boolean isExcludedOutboundHeader(String headerName) {
        for (String excludedHeader : EXCLUDED_OUTBOUND_HEADERS) {
            if (excludedHeader.equalsIgnoreCase(headerName)) {
                return true;
            }
        }
        return false;
    }

    private static HttpHeaders copyHeaders(HttpHeaders source) {
        HttpHeaders copy = new HttpHeaders();
        if (source != null) {
            source.forEach((name, values) -> copy.put(name, values));
        }
        return copy;
    }
}