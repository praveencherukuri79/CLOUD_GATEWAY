package com.example.bff.service;

import com.example.bff.config.JwksKeyProvider;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InternalJwtService {

    @Value("${app.internal-jwt.issuer}")
    String issuer;

    @Value("${app.internal-jwt.audience}")
    String audience;

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

    public String createToken(Authentication authentication, String ipAddress) {
        Instant now = Instant.now();
        Object principal = authentication.getPrincipal();

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

        if (principal instanceof OidcUser oidc) {
            String username = StringUtils.hasText(oidc.getClaimAsString("preferred_username"))
                    ? oidc.getClaimAsString("preferred_username")
                    : oidc.getName();
            claims.claim("username", username);

            if (StringUtils.hasText(oidc.getEmail())) {
                claims.claim("email", oidc.getEmail());
            }
        } else if (principal instanceof AuthenticatedPrincipal named) {
            claims.claim("username", named.getName());
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
