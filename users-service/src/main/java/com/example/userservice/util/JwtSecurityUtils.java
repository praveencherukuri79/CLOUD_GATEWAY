package com.example.userservice.util;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

public final class JwtSecurityUtils {

    private JwtSecurityUtils() {}

        public static JwtDecoder jwtDecoder(Resource publicKeyLocation, String issuer, String audience) {
        NimbusJwtDecoder decoder =
            NimbusJwtDecoder.withPublicKey(loadPublicKey(publicKeyLocation)).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        JwtClaimValidator<List<String>> audienceValidator =
            new JwtClaimValidator<>("aud", aud -> aud != null && aud.contains(audience));
        decoder.setJwtValidator(
            new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return decoder;
    }

    public static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    private static RSAPublicKey loadPublicKey(Resource publicKeyLocation) {
        try {
            String pem = publicKeyLocation.getContentAsString(StandardCharsets.UTF_8);
            String normalizedKey =
                    pem.replace("-----BEGIN PUBLIC KEY-----", "")
                            .replace("-----END PUBLIC KEY-----", "")
                            .replaceAll("\\s+", "");

            byte[] keyBytes = Base64.getDecoder().decode(normalizedKey);
            return (RSAPublicKey)
                    KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to load RSA public key from PEM file: " + publicKeyLocation, ex);
        }
    }
}
