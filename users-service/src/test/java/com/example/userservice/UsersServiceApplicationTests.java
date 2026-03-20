package com.example.userservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
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

    @Value("file:../src/main/resources/keys/bff-jwt-private.pem")
    Resource privateKeyFile;

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
        String pem = privateKeyFile.getContentAsString(StandardCharsets.UTF_8);
        String normalizedKey =
            pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] keyBytes = Base64.getDecoder().decode(normalizedKey);
        PrivateKey privateKey =
            KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
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