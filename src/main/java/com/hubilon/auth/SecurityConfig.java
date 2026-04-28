package com.hubilon.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Default security configuration applied when no {@link SecurityFilterChain} bean
 * is defined by the consuming application.
 *
 * <p>If you need a custom {@link SecurityFilterChain}, define your own bean and inject
 * {@link KeycloakTokenFilter} manually:
 * <pre>{@code
 * @Bean
 * public SecurityFilterChain securityFilterChain(HttpSecurity http,
 *                                                 KeycloakTokenFilter filter) throws Exception {
 *     return http
 *         .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
 *         ...
 *         .build();
 * }
 * }</pre>
 */
@Configuration
@EnableMethodSecurity
class SecurityConfig implements WebMvcConfigurer {

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
                                                   KeycloakTokenFilter filter,
                                                   KeycloakProperties properties) throws Exception {

        String[] permitPaths = properties.getPermitAllPaths().toArray(String[]::new);

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                if (permitPaths.length > 0) {
                    auth.requestMatchers(permitPaths).permitAll();
                }
                auth.anyRequest().authenticated();
            })
            .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }
}
