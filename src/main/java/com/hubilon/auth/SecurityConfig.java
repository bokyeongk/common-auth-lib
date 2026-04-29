package com.hubilon.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 기본 Spring Security 설정.
 *
 * <p>소비 서비스에 {@link SecurityFilterChain} 빈이 없을 때만 활성화됩니다.
 *
 * <h3>CSRF 보호 전략</h3>
 * <ul>
 *   <li>서버: {@code XSRF-TOKEN} 쿠키 발급 (HttpOnly=false, JS 읽기 가능)</li>
 *   <li>클라이언트: 쿠키 값을 읽어 {@code X-XSRF-TOKEN} 헤더로 전송</li>
 *   <li>GET/HEAD/OPTIONS/TRACE는 CSRF 검증 제외 (RFC 표준)</li>
 * </ul>
 *
 * <h3>커스텀 SecurityFilterChain 사용 시</h3>
 * <pre>{@code
 * @Bean
 * public SecurityFilterChain securityFilterChain(HttpSecurity http,
 *                                                 KeycloakTokenFilter filter) throws Exception {
 *     CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
 *     csrfRepo.setCookieCustomizer(c -> c.sameSite("Strict"));
 *
 *     return http
 *         .csrf(csrf -> csrf
 *             .csrfTokenRepository(csrfRepo)
 *             .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
 *         )
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

        // XSRF-TOKEN 쿠키: HttpOnly=false (JS에서 읽어 X-XSRF-TOKEN 헤더로 전송)
        // SameSite=Strict: 크로스사이트 요청 시 쿠키 전송 차단
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookieCustomizer(cookie -> cookie.sameSite("Strict"));

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepo)
                // XOR 인코딩 없이 쿠키 원본값을 헤더로 그대로 전송 (SPA 친화적)
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                // 인증 플로우 엔드포인트: CSRF 토큰 없이 동작 (logout/refresh는 HttpOnly 쿠키로 보호)
                .ignoringRequestMatchers("/auth/logout", "/auth/refresh")
            )
            // OAuth 콜백 state 파라미터 세션 저장을 위해 IF_REQUIRED 사용
            // API 필터(KeycloakTokenFilter)는 JWT 쿠키 기반으로 무상태 동작
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
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
