package com.example.userservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(UsersServiceApplicationTests.JwksTestConfig.class)
class UsersServiceApplicationTests {

    static final KeyPair KEY_PAIR;

    static {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KEY_PAIR = generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate test RSA key pair", ex);
        }
    }

    @TestConfiguration
    static class JwksTestConfig {
        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            return NimbusJwtDecoder
                    .withPublicKey((RSAPublicKey) KEY_PAIR.getPublic())
                    .build();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void pingShouldBePublic() throws Exception {
           mockMvc.perform(get("/public/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("users service is running"));
    }

    @Test
    void usersEndpointShouldRequireJwt() throws Exception {
           mockMvc.perform(get("/users/101")).andExpect(status().isUnauthorized());
    }

    @Test
    void usersEndpointShouldAcceptBffSignedJwt() throws Exception {
        String token = createSignedToken("demoUser123", List.of("ROLE_USER"));

        mockMvc.perform(
                        get("/users/101")
                                .header("Authorization", "Bearer " + token)
                                .header("X-APP-User", "demoUser123")
                                .header("X-Correlation-Id", "corr-101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("101"))
                .andExpect(jsonPath("$.jwtSubject").value("demoUser123"))
                .andExpect(jsonPath("$.jwtIssuer").value("bff-service"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"))
                .andExpect(jsonPath("$.xAppUser").value("demoUser123"))
                .andExpect(jsonPath("$.correlationId").value("corr-101"));
    }

    private String createSignedToken(String subject, List<String> roles) throws Exception {
        RSAPrivateKey privateKey = (RSAPrivateKey) KEY_PAIR.getPrivate();
        Instant now = Instant.now();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("bff-service")
                .subject(subject)
                .audience("internal-api")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("roles", roles)
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
                claims);
        jwt.sign(new RSASSASigner(privateKey));
        return jwt.serialize();
    }
}