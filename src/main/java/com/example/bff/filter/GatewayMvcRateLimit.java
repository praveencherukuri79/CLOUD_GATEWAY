package com.example.bff.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Component
public class GatewayMvcRateLimit {

    @Value("${app.proxy.rpm:200}")
    private int rpm;

    private volatile Bucket bucket;

    public HandlerFilterFunction<ServerResponse, ServerResponse> asFilter() {
        return (req, next) -> bucket().tryConsume(1) ? next.handle(req) : ServerResponse.status(429).build();
    }

    private Bucket bucket() {
        Bucket b = bucket;
        if (b == null) {
            b = Bucket.builder().addLimit(Bandwidth.simple(Math.max(1, rpm), Duration.ofMinutes(1))).build();
            bucket = b;
        }
        return b;
    }
}
