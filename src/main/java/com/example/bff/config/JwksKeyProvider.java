package com.example.bff.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Loads the RSA key pair from PEM files on the classpath and publishes
 * the public key as a JSON Web Key Set (JWKS).
 */
@Component
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JwksKeyProvider {

    @Value("${app.internal-jwt.private-key-location}")
    Resource privateKeyResource;

    RSAPublicKey publicKey;
    RSAPrivateKey privateKey;
    String keyId;
    JWKSet jwkSet;

    @PostConstruct
    void init() {
        try {
            this.privateKey = loadPrivateKey(privateKeyResource);
            this.publicKey = derivePublicKey(privateKey);
            this.keyId = generateKeyId(publicKey);

            RSAKey rsaKey = new RSAKey.Builder(this.publicKey)
                    .keyID(this.keyId)
                    .build();

            this.jwkSet = new JWKSet(rsaKey);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load RSA key pair for JWKS", ex);
        }
    }

    private static RSAPrivateKey loadPrivateKey(Resource resource) throws Exception {
        String pem = resource.getContentAsString(StandardCharsets.UTF_8);
        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private static RSAPublicKey derivePublicKey(RSAPrivateKey privateKey) throws Exception {
        RSAPrivateCrtKey crtKey = (RSAPrivateCrtKey) privateKey;
        RSAPublicKeySpec pubSpec =
                new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent());
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(pubSpec);
    }

    private static String generateKeyId(RSAPublicKey publicKey) throws JOSEException {
        return new RSAKey.Builder(publicKey).build().computeThumbprint().toString();
    }
}
