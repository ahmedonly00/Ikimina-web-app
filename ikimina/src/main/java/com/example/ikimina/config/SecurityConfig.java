package com.example.ikimina.config;

import com.example.ikimina.security.JwtAccessDeniedHandler;
import com.example.ikimina.security.JwtAuthenticationEntryPoint;
import com.example.ikimina.security.JwtAuthenticationFilter;
import com.example.ikimina.security.JwtTokenProvider;
import com.example.ikimina.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
// Spring Boot 4 split actuator support across modules: EndpointRequest now
// lives in spring-boot-security and HealthEndpoint in spring-boot-health.
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
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
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    @Value("${ikimina.cors.allowed-origins}")
    private String[] allowedOrigins;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
                          JwtTokenProvider jwtTokenProvider,
                          JwtAuthenticationEntryPoint authenticationEntryPoint,
                          JwtAccessDeniedHandler accessDeniedHandler) {
        this.userDetailsService = userDetailsService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // Stateless bearer-token API: there is no session cookie for an
            // attacker to ride, so CSRF tokens add nothing here.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/auth/login",
                    "/api/auth/register",
                    // Minimal group list that populates the login/register
                    // group picker, which is necessarily pre-authentication.
                    "/api/savings-groups/public",
                    // Provider webhooks cannot carry a user token; the HMAC
                    // signature is the authentication (see PaymentWebhookController).
                    // Unsigned or stale requests are rejected inside the adapter.
                    "/api/webhooks/payments/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Liveness/readiness must be reachable by the orchestrator
                // without credentials. Detail is still withheld from anonymous
                // callers (management.endpoint.health.show-details=when-authorized),
                // so this exposes up/down and nothing about the infrastructure.
                .requestMatchers(
                    EndpointRequest.to(HealthEndpoint.class),
                    EndpointRequest.to(InfoEndpoint.class)
                ).permitAll()
                // Metrics describe internals and are operator-only.
                .requestMatchers(EndpointRequest.toAnyEndpoint()).hasRole("SUPER_ADMIN")
                // Role names here are WITHOUT the ROLE_ prefix: hasRole()
                // prepends it. The enum constants are ROLE_SUPER_ADMIN etc.
                .requestMatchers("/api/super-admin/**").hasRole("SUPER_ADMIN")
                .anyRequest().authenticated()
            );

        http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Not exposed as a bean: a Filter bean would also be auto-registered with
     * the servlet container and run a second time on every request.
     */
    private JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider);
    }

    /**
     * Spring Security 7 takes the UserDetailsService as a constructor argument;
     * the no-arg constructor and setUserDetailsService were removed.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        // Report "bad credentials" rather than "user not found" so the login
        // endpoint cannot be used to enumerate registered emails.
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    /**
     * Built explicitly from the DAO provider. Deriving it from the shared
     * HttpSecurity builder instead yields a manager whose provider list is not
     * the one configured here, so every login fails with "bad credentials".
     */
    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("X-Subscription-Warning"));
        // Tokens travel in the Authorization header, not cookies.
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
