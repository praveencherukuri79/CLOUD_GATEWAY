package com.example.userservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class UsersServiceApplicationTests {

    @Autowired
    MockMvc mockMvc;

    @Value("${app.security.jwt.secret}")
    String secret;

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
        Instant now = Instant.now();
        SecretKey signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .issuer("bff-service")
                .subject(subject)
                .audience()
                .add("internal-api")
                .and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .claims(Map.of("roles", roles))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }
}