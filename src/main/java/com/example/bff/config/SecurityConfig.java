package com.example.bff.config;

import com.example.bff.security.RoleAwareLoginSuccessHandler;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SecurityConfig {

    RoleAwareLoginSuccessHandler loginSuccessHandler;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/login", "/error", "/test-client.html",
                                                "/.well-known/jwks.json", "/role-selection")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .formLogin(form -> form.successHandler(loginSuccessHandler))
                .build();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails singleRoleUser =
                User.withUsername("demoUser123")
                        .password(passwordEncoder.encode("password"))
                        .roles("USER")
                        .build();

        UserDetails multiRoleUser =
                User.withUsername("adminUser")
                        .password(passwordEncoder.encode("password"))
                        .roles("USER", "ADMIN")
                        .build();

        return new InMemoryUserDetailsManager(singleRoleUser, multiRoleUser);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
