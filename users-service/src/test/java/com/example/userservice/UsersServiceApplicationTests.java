package com.example.userservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class UsersServiceApplicationTests {

    @Autowired
    MockMvc mockMvc;

    @Value("file:../src/main/resources/keys/bff-jwt-keystore.p12")
    Resource privateKeyStore;

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
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream inputStream = privateKeyStore.getInputStream()) {
            keyStore.load(inputStream, "changeit".toCharArray());
        }

        PrivateKey privateKey = (PrivateKey) keyStore.getKey("bff-jwt-key", "changeit".toCharArray());
        Instant now = Instant.now();

        return Jwts.builder()
                .issuer("bff-service")
                .subject(subject)
                .audience()
                .add("internal-api")
                .and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .claims(Map.of("roles", roles))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }
}