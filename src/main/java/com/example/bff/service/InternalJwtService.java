package com.example.bff.service;

import com.example.bff.config.JwksKeyProvider;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
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

    public String createToken(Authentication authentication, String ipAddress) {
        Instant now = Instant.now();

        List<String> roles =
                authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList();

        LinkedHashMap<String, Object> claims = new LinkedHashMap<>();
        claims.put("roles", roles);
        addOidcClaims(authentication, claims);

        if (StringUtils.hasText(ipAddress)) {
            claims.put("ipAddress", ipAddress);
        }

        return Jwts.builder()
                .header().keyId(jwksKeyProvider.getKeyId()).and()
                .issuer(issuer)
                .subject(authentication.getName())
                .audience()
                .add(audience)
                .and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .claims(claims)
                .signWith(jwksKeyProvider.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    private void addOidcClaims(Authentication authentication, LinkedHashMap<String, Object> claims) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof OidcUser oidcUser) {
            putClaimIfPresent(claims, "username", resolveOidcUsername(oidcUser));
            putClaimIfPresent(claims, "email", oidcUser.getEmail());
            return;
        }

        if (principal instanceof AuthenticatedPrincipal authenticatedPrincipal) {
            putClaimIfPresent(claims, "username", authenticatedPrincipal.getName());
        }
    }

    private String resolveOidcUsername(OidcUser oidcUser) {
        String preferredUsername = oidcUser.getClaimAsString("preferred_username");
        if (StringUtils.hasText(preferredUsername)) {
            return preferredUsername;
        }

        String name = oidcUser.getName();
        return StringUtils.hasText(name) ? name : null;
    }

    private void putClaimIfPresent(LinkedHashMap<String, Object> claims, String claimName, String claimValue) {
        if (StringUtils.hasText(claimValue)) {
            claims.put(claimName, claimValue);
        }
    }
}
