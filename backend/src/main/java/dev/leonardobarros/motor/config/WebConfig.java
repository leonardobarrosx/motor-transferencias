package dev.leonardobarros.motor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import dev.leonardobarros.motor.shared.web.Headers;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties.class)
class WebConfig implements WebMvcConfigurer {

    private final CorsProperties cors;

    WebConfig(CorsProperties cors) {
        this.cors = cors;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(cors.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", Headers.IDEMPOTENCY_KEY)
                .exposedHeaders("Location", Headers.IDEMPOTENT_REPLAYED, "Retry-After");
    }
}
