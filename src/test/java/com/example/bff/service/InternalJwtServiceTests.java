package com.example.bff.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bff.security.AuthContext;
import com.nimbusds.jwt.SignedJWT;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@SpringBootTest
class InternalJwtServiceTests {

    @Autowired
    InternalJwtService internalJwtService;

    @Test
    void createsTokenFromAuthContextClaims() throws Exception {
        AuthContext ctx = AuthContext.builder()
                .userId("user-123")
                .username("demoUser123")
                .tenantId("tenant-abc")
                .selectedRoleId("ROLE_USER")
                .selectedRoleType("STANDARD")
                .email("demo@example.com")
                .claims(Map.of("some", "claim"))
                .build();

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                ctx,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN")));

        String jwt = internalJwtService.createToken(auth, "127.0.0.1", "ROLE_USER");
        SignedJWT parsed = SignedJWT.parse(jwt);
        Map<String, Object> claims = parsed.getJWTClaimsSet().getClaims();

        assertThat(claims.get("sub")).isEqualTo("demoUser123");
        assertThat(claims.get("username")).isEqualTo("demoUser123");
        assertThat(claims.get("tenantId")).isEqualTo("tenant-abc");
        assertThat(claims.get("activeRole")).isEqualTo("ROLE_USER");
        assertThat(claims.get("activeRoleType")).isEqualTo("STANDARD");
        assertThat(claims.get("email")).isEqualTo("demo@example.com");

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) claims.get("roles");
        assertThat(roles).contains("ROLE_USER", "ROLE_ADMIN");
        assertThat(claims.get("ipAddress")).isEqualTo("127.0.0.1");
    }
}
