package com.example.userservice.config;

import com.example.userservice.model.RolePermissions;
import com.example.userservice.service.ConfigServiceClient;
import com.example.userservice.util.JwtSecurityUtils;
import java.util.ArrayList;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;


//Authoritative references:

//Spring docs: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html
//JWT section: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.security.jwt.jwks-uri}")
    private String jwksUri;

    @Value("${app.security.jwt.issuer}")
    private String issuer;

    @Value("${app.security.jwt.audience}")
    private String audience;

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {

        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth ->
                        auth.requestMatchers("/public/ping").permitAll()
                                .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return JwtSecurityUtils.jwtDecoder(jwksUri, issuer, audience);
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(ConfigServiceClient configServiceClient) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> buildAuthorities(jwt, configServiceClient));
        return converter;
    }

    private Collection<GrantedAuthority> buildAuthorities(Jwt jwt, ConfigServiceClient configServiceClient) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();

        var roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
            roles.forEach(role -> authorities.add(new SimpleGrantedAuthority(role)));
        }

        String activeRole = jwt.getClaimAsString("activeRole");
        if (activeRole != null && !activeRole.isBlank()) {
            RolePermissions perms = configServiceClient.getPermissions(activeRole);
            if (perms != null && perms.features() != null) {
                perms.features().forEach((feature, featurePerms) ->
                        featurePerms.forEach((perm, enabled) -> {
                            if (Boolean.TRUE.equals(enabled)) {
                                authorities.add(new SimpleGrantedAuthority(feature + "." + perm));
                            }
                        }));
            }
        }

        return authorities;
    }
}
