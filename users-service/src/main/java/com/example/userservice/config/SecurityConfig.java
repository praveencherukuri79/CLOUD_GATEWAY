package com.example.userservice.config;

import com.example.userservice.util.JwtSecurityUtils;
import com.example.userservice.security.AuthContextJwtAuthenticationConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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
            JwtDecoder jwtDecoder)
            throws Exception {

        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth ->
                        auth.requestMatchers("/public/ping").permitAll()
                                .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 ->
                    oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)
                        .jwtAuthenticationConverter(new AuthContextJwtAuthenticationConverter())))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return JwtSecurityUtils.jwtDecoder(jwksUri, issuer, audience);
    }
}
