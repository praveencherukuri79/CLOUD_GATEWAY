package com.example.bff.service;

import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InternalJwtService {

    @Value("${app.internal-jwt.issuer}")
    String issuer;

    @Value("${app.internal-jwt.audience}")
    String audience;

    @Value("${app.internal-jwt.ttl-seconds}")
    long ttlSeconds;

    @Value("${app.internal-jwt.private-key-store-location}")
    Resource privateKeyStoreLocation;

    @Value("${app.internal-jwt.private-key-store-password}")
    String privateKeyStorePassword;

    @Value("${app.internal-jwt.private-key-alias}")
    String privateKeyAlias;

    @Value("${app.internal-jwt.private-key-password}")
    String privateKeyPassword;

    PrivateKey signingKey;

    @PostConstruct
    void loadSigningKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream inputStream = privateKeyStoreLocation.getInputStream()) {
            keyStore.load(inputStream, privateKeyStorePassword.toCharArray());
        }

        this.signingKey =
                (PrivateKey)
                        keyStore.getKey(privateKeyAlias, privateKeyPassword.toCharArray());
    }

    public String createToken(Authentication authentication) {
        Instant now = Instant.now();

        List<String> roles =
                authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList();

        return Jwts.builder()
                .issuer(issuer)
                .subject(authentication.getName())
                .audience()
                .add(audience)
                .and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .claims(Map.of("roles", roles))
                .signWith(signingKey, Jwts.SIG.RS256)
                .compact();
    }
}
