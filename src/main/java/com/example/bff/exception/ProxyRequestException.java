package com.example.bff.exception;

import org.springframework.http.HttpStatus;

public class ProxyRequestException extends RuntimeException {

    private final HttpStatus status;
    private final String correlationId;

    public ProxyRequestException(HttpStatus status, String message, String correlationId) {
        super(message);
        this.status = status;
        this.correlationId = correlationId;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
