package com.example.bff.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ProxyRequestException extends RuntimeException {

    private final HttpStatus status;
    private final String correlationId;

    public ProxyRequestException(HttpStatus status, String message, String correlationId) {
        super(message);
        this.status = status;
        this.correlationId = correlationId;
    }
}
