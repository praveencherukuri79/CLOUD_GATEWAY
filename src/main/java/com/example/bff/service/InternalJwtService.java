package com.example.bff.service;

import com.example.bff.config.JwksKeyProvider;
import com.example.bff.security.AuthContext;
import com.example.bff.security.AuthUtils;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InternalJwtService {

    @Value("${app.internal-jwt.issuer}")
    String issuer;

    @Value("#{'${app.internal-jwt.audience}'.split(',')}")
    List<String> audience;

    @Value("${app.internal-jwt.ttl-seconds}")
    long ttlSeconds;

    final JwksKeyProvider jwksKeyProvider;

    JWSHeader jwsHeader;

    @PostConstruct
    void init() {
        jwsHeader = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(jwksKeyProvider.getKeyId())
                .build();
    }

    public String createToken(Authentication authentication, String ipAddress, String activeRole) {
        Instant now = Instant.now();
        AuthContext authContext = AuthUtils.authContext(authentication);

        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(authentication.getName())
                .audience(audience)
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
                .jwtID(UUID.randomUUID().toString())
                .claim("roles", authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList());

        if (StringUtils.hasText(activeRole)) {
            claims.claim("activeRole", activeRole);
        }

        if (authContext != null) {
            if (StringUtils.hasText(authContext.getUsername())) {
                claims.claim("username", authContext.getUsername());
            }
            if (StringUtils.hasText(authContext.getTenantId())) {
                claims.claim("tenantId", authContext.getTenantId());
            }
            if (StringUtils.hasText(authContext.getSelectedRoleType())) {
                claims.claim("activeRoleType", authContext.getSelectedRoleType());
            }
            if (authContext.getEmail() != null && StringUtils.hasText(authContext.getEmail())) {
                claims.claim("email", authContext.getEmail());
            }
        }

        if (authContext == null || !StringUtils.hasText(authContext.getUsername())) {
            claims.claim("username", authentication.getName());
        }

        if (StringUtils.hasText(ipAddress)) {
            claims.claim("ipAddress", ipAddress);
        }

        try {
            SignedJWT jwt = new SignedJWT(jwsHeader, claims.build());
            jwt.sign(new RSASSASigner(jwksKeyProvider.getPrivateKey()));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Failed to sign JWT", ex);
        }
    }
}
