
package com.bookstore.book_shopping_app.config;

import java.util.Locale;

import com.bookstore.book_shopping_app.constants.BookstoreErrorConstants;
import com.bookstore.book_shopping_app.constants.BookstoreMessageConstants;
import com.bookstore.book_shopping_app.exception.ApiProblemWriter;
import com.bookstore.book_shopping_app.repository.UserRepository;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmailIgnoreCase(
                        email.trim().toLowerCase(Locale.ROOT)
                )
                .map(user -> org.springframework.security.core.userdetails.User
                        .withUsername(user.getEmail())
                        .password(user.getPasswordHash())
                        .authorities(user.getRole().name())
                        .build())
                .orElseThrow(() ->
                        new UsernameNotFoundException("Invalid credentials"));
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiProblemWriter problemWriter
    ) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        ))

                .exceptionHandling(exception -> exception

                        .authenticationEntryPoint((request, response, ex) ->
                                problemWriter.write(
                                        response,
                                        HttpStatus.UNAUTHORIZED,
                                        BookstoreErrorConstants.AUTHENTICATION_REQUIRED,
                                        BookstoreMessageConstants.AUTHENTICATION_REQUIRED_MESSAGE
                                )
                        )

                        .accessDeniedHandler((request, response, ex) ->
                                problemWriter.write(
                                        response,
                                        HttpStatus.FORBIDDEN,
                                        BookstoreErrorConstants.ACCESS_DENIED,
                                        BookstoreMessageConstants.ACCESS_DENIED_MESSAGE
                                )
                        )
                )

                .authorizeHttpRequests(authorize -> authorize

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/payments/webhook"
                        ).permitAll()

                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/books",
                                "/api/books/**"
                        ).permitAll()

                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        .requestMatchers("/api/books/**").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )

                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
