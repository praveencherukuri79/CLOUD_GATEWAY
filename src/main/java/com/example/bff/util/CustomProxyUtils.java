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
        String requestPath = normalizeRequestPath(request);
        String downstreamPath = resolveDownstreamPath(requestPath, routePrefix);

        String normalizedBaseUri = targetBaseUri.endsWith("/")
                        ? targetBaseUri.substring(0, targetBaseUri.length() - 1)
                        : targetBaseUri;
        String queryString = request.getQueryString();

        return normalizedBaseUri + downstreamPath + (StringUtils.hasText(queryString) ? "?" + queryString : "");
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
        String downstreamPath = requestPath;

        if (requestPath.startsWith(routePrefix)) {
            downstreamPath = requestPath.substring(routePrefix.length());
        }

        if (!StringUtils.hasText(downstreamPath)) {
            return "/";
        }

        return downstreamPath.startsWith("/") ? downstreamPath : "/" + downstreamPath;
    }

    public static HttpHeaders buildOutboundHeaders(
            HttpServletRequest request, String appUser, String correlationId, String token) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames != null && headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();

            if (headerName.equalsIgnoreCase(HttpHeaders.HOST)
                    || headerName.equalsIgnoreCase(HttpHeaders.COOKIE)
                    || headerName.equalsIgnoreCase(HttpHeaders.AUTHORIZATION)
                    || headerName.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH)) {
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