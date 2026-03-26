package com.example.bff.controller;

import com.example.bff.config.JwksKeyProvider;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the JWKS endpoint so downstream services can fetch
 * the public key(s) used to verify JWTs signed by this gateway.
 */
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JwksController {

    JwksKeyProvider jwksKeyProvider;

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return jwksKeyProvider.getJwkSet().toJSONObject();
    }
}
