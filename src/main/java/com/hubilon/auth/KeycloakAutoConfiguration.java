package com.hubilon.auth;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(SecurityFilterChain.class)
@EnableConfigurationProperties(KeycloakProperties.class)
@Import(SecurityConfig.class)
public class KeycloakAutoConfiguration {

    @Bean
    public JwtDecoder keycloakJwtDecoder(KeycloakProperties properties) {
        return NimbusJwtDecoder.withJwkSetUri(properties.getJwksUri()).build();
    }

    @Bean
    public KeycloakTokenFilter keycloakTokenFilter(JwtDecoder keycloakJwtDecoder,
                                                   KeycloakProperties properties) {
        return new KeycloakTokenFilter(keycloakJwtDecoder, properties);
    }

    @Bean
    public KeycloakClient keycloakClient(KeycloakProperties properties) {
        return new KeycloakClient(properties, new RestTemplate());
    }
}
