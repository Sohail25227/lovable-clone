package com.aibuilder.lovableclone.common.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.aibuilder.lovableclone.common.security.JwtAuthFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final String frontendOrigin;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          @Value("${app.frontend-origin}") String frontendOrigin) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.frontendOrigin = frontendOrigin;
    }

    /**
     * Preview ki apni chain, kyunki uske headers baaki API se ulte hain.
     *
     * Spring Security har response pe X-Frame-Options: DENY lagata hai, aur DENY ka matlab
     * same origin se bhi framing nahi. Preview ko iframe mein chalna hai, to yahan woh header
     * hatta hai — sirf in paths pe, taaki API kahin embed na ki ja sake. Uski jagah CSP ka
     * frame-ancestors aata hai, jo X-Frame-Options ke ulte ek origin naam se allow kar sakta hai.
     *
     * Yahan JwtAuthFilter ki zarurat nahi: permission URL ke signed token se aati hai,
     * jise PreviewController khud verify karta hai.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain previewFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/preview/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            // Frontend alag origin pe chalta hai, to browser pehle preflight bhejta hai.
            // Yeh Spring Security ke andar hai, kyunki bina iske OPTIONS request filter
            // chain mein hi 401 ho jaati aur asli request kabhi nahi jaati
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/**", "/api/health").permitAll()
                    .anyRequest().authenticated()
                ).addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Ek naam wali origin, "*" nahi: "*" har website ko user ke browser se hamari API
        // par request karne deta hai
        config.setAllowedOrigins(List.of(frontendOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        // allowCredentials jaan-boojh ke off hai. Token Authorization header se jata hai,
        // cookie se nahi, to browser ko cookies bhejne ki ijazat dene ki zarurat nahi —
        // aur usse CSRF ka woh raasta khulta hai jo stateless JWT mein nahi hai
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);

        return source;
    }
}
