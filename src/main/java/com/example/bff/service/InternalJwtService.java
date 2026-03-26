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
import java.util.LinkedHashMap;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
                .issuer(issuer)
                .subject(authentication.getName())
                .audience()
                .add(audience)
                .and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                                .claims(claims)
                .signWith(signingKey, Jwts.SIG.RS256)
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
