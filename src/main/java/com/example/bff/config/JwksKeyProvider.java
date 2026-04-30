package com.example.bff.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.cert.Certificate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Loads the RSA key pair from a PKCS#12 keystore on the classpath and publishes
 * the public key as a JSON Web Key Set (JWKS).
 */
@Component
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JwksKeyProvider {

    @Value("${app.internal-jwt.keystore-location}")
    Resource keystoreResource;

    @Value("${app.internal-jwt.keystore-password}")
    String keystorePassword;

    @Value("${app.internal-jwt.key-alias}")
    String keyAlias;

    RSAPublicKey publicKey;
    RSAPrivateKey privateKey;
    String keyId;
    JWKSet jwkSet;

    @PostConstruct
    void init() {
        try {
            LoadedRsaKeys rsaKeys = loadKeys(
                keystoreResource,
                keystorePassword,
                keyAlias);

            this.privateKey = rsaKeys.privateKey();
            this.publicKey = rsaKeys.publicKey();
            this.keyId = generateKeyId(publicKey);

            RSAKey rsaKey = new RSAKey.Builder(this.publicKey)
                    .keyID(this.keyId)
                    .build();

            this.jwkSet = new JWKSet(rsaKey);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load RSA key pair for JWKS", ex);
        }
    }

    private static LoadedRsaKeys loadKeys(
            Resource resource,
            String keystorePassword,
            String keyAlias) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream inputStream = resource.getInputStream()) {
            keyStore.load(inputStream, keystorePassword.toCharArray());
        }

        PrivateKey privateKey = (PrivateKey) keyStore.getKey(keyAlias, keystorePassword.toCharArray());
        if (!(privateKey instanceof RSAPrivateKey rsaPrivateKey)) {
            throw new IllegalStateException("Key entry '" + keyAlias + "' is not an RSA private key");
        }

        Certificate certificate = keyStore.getCertificate(keyAlias);
        if (certificate == null || !(certificate.getPublicKey() instanceof RSAPublicKey rsaPublicKey)) {
            throw new IllegalStateException("Certificate for alias '" + keyAlias + "' does not contain an RSA public key");
        }

        return new LoadedRsaKeys(rsaPrivateKey, rsaPublicKey);
    }

    private static String generateKeyId(RSAPublicKey publicKey) throws JOSEException {
        return new RSAKey.Builder(publicKey).build().computeThumbprint().toString();
    }

    private record LoadedRsaKeys(RSAPrivateKey privateKey, RSAPublicKey publicKey) {
    }
}
