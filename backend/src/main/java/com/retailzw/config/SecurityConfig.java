package com.retailzw.config;

import com.retailzw.security.jwt.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    @Order(1)
    public SecurityFilterChain platformAdminFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/admin/**", "/auth/admin/**", "/auth/signup", "/checkout/**")
            .csrf(csrf -> csrf.ignoringRequestMatchers("/checkout/smilepay/webhook"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/admin/**", "/auth/signup", "/checkout/**").permitAll()
                .anyRequest().hasRole("SAAS_ADMIN")
            )
            .formLogin(form -> form
                .loginPage("/auth/admin/login")
                .loginProcessingUrl("/auth/admin/login")
                .defaultSuccessUrl("/admin/dashboard", true)
                .failureUrl("/auth/admin/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/auth/admin/logout")
                .logoutSuccessUrl("/auth/admin/login?logout=true")
                .permitAll()
            )
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            );
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain shopFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/shop/**", "/auth/shop/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/shop/**").permitAll()
                .requestMatchers(HttpMethod.GET,
                        "/shop/dashboard",
                        "/shop/supervisor",
                        "/shop/sales",
                        "/shop/cash",
                        "/shop/change",
                        "/shop/returns",
                        "/shop/purchasing",
                        "/shop/gas",
                        "/shop/gas/sales",
                        "/shop/gas/sales/export",
                        "/shop/gas/change",
                        "/shop/gas/restocking",
                        "/shop/gas/restocking/export",
                        "/shop/gas/tanks",
                        "/shop/gas/accounting",
                        "/shop/notifications",
                        "/shop/support/chat/feed")
                    .hasAnyRole("SUPER_ADMIN", "ACCOUNTANT", "SUPERVISOR")
                .requestMatchers(HttpMethod.POST,
                        "/shop/cash/shifts/collect",
                        "/shop/change/*/collect",
                        "/shop/purchasing/*/receive",
                        "/shop/gas/change/*/collect",
                        "/shop/gas/restocks",
                        "/shop/notifications/read-all",
                        "/shop/support/chat")
                    .hasAnyRole("SUPER_ADMIN", "ACCOUNTANT", "SUPERVISOR")
                .anyRequest().hasAnyRole("SUPER_ADMIN", "ACCOUNTANT")
            )
            .formLogin(form -> form
                .loginPage("/auth/shop/login")
                .loginProcessingUrl("/auth/shop/login")
                .defaultSuccessUrl("/shop/dashboard", false)
                .failureUrl("/auth/shop/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/auth/shop/logout")
                .logoutSuccessUrl("/auth/shop/login?logout=true")
                .permitAll()
            )
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            );
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/api/public/releases/**").permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Tenant-Code", "X-Request-ID"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}

