package com.example.bff.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
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

    SecretKey signingKey;

    @Value("${app.internal-jwt.secret}")
    public void setSecret(String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
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
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }
}
