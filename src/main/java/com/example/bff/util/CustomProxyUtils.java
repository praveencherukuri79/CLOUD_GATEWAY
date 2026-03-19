package com.example.bff.util;

import com.example.bff.filter.InternalJwtRelayFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

public final class CustomProxyUtils {

    private static final String PROXY_MODE_HEADER = "X-Proxy-Mode";
    private static final String CUSTOM_PROXY_MODE = "custom";

    private CustomProxyUtils() {}

    public static String resolveCorrelationId(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(InternalJwtRelayFilter.CORRELATION_ID_HEADER))
                .filter(StringUtils::hasText)
                .orElseGet(() -> UUID.randomUUID().toString());
    }

    public static String buildTargetUri(
            HttpServletRequest request, String routePrefix, String targetBaseUri) {
        String requestPath = request.getRequestURI();
        String downstreamPath =
                requestPath.startsWith(routePrefix) ? requestPath.substring(routePrefix.length()) : "";

        if (!StringUtils.hasText(downstreamPath)) {
            downstreamPath = "/";
        }

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

    public static HttpHeaders buildOutboundHeaders(
            HttpServletRequest request, String appUser, String correlationId, String token) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames != null && headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();

            if (HttpHeaders.HOST.equalsIgnoreCase(headerName)
                    || HttpHeaders.COOKIE.equalsIgnoreCase(headerName)
                    || HttpHeaders.AUTHORIZATION.equalsIgnoreCase(headerName)
                    || HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(headerName)) {
                continue;
            }

            Enumeration<String> headerValues = request.getHeaders(headerName);
            while (headerValues.hasMoreElements()) {
                headers.add(headerName, headerValues.nextElement());
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

    private static HttpHeaders copyHeaders(HttpHeaders source) {
        HttpHeaders copy = new HttpHeaders();
        if (source != null) {
            source.forEach((name, values) -> copy.put(name, values));
        }
        return copy;
    }
}