package com.example.bff.service;

import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
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

    @Value("${app.internal-jwt.private-key-location}")
    Resource privateKeyLocation;

    PrivateKey signingKey;

    @PostConstruct
    void loadSigningKey() throws Exception {
        String pem = privateKeyLocation.getContentAsString(StandardCharsets.UTF_8);
        String normalizedKey =
                pem.replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s+", "");

        byte[] keyBytes = Base64.getDecoder().decode(normalizedKey);
        this.signingKey =
                KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
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
