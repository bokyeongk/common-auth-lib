package com.hubilon.auth;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(SecurityFilterChain.class)
@EnableConfigurationProperties(KeycloakProperties.class)
@Import(SecurityConfig.class)
public class KeycloakAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAutoConfiguration.class);

    @Bean
    public JwtDecoder keycloakJwtDecoder(KeycloakProperties properties) {
        List<String> missing = properties.getMissingRequiredFields();
        if (!missing.isEmpty()) {
            String msg = "Required Keycloak properties are missing: " + String.join(", ", missing);
            log.warn("[Keycloak] {}", msg);
            KeycloakConfigurationException cause = new KeycloakConfigurationException(msg);
            return token -> { throw new JwtException("Keycloak configuration error", cause); };
        }
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

    @Bean
    public KeycloakAuthService keycloakAuthService(KeycloakClient keycloakClient,
                                                   KeycloakProperties properties) {
        return new KeycloakAuthService(keycloakClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean(KeycloakAuthController.class)
    @ConditionalOnProperty(name = "keycloak.auth-controller.enabled", havingValue = "true", matchIfMissing = true)
    public KeycloakAuthController keycloakAuthController(KeycloakClient keycloakClient,
                                                         KeycloakProperties properties,
                                                         KeycloakAuthService keycloakAuthService) {
        return new KeycloakAuthController(keycloakClient, properties, keycloakAuthService);
    }
}
