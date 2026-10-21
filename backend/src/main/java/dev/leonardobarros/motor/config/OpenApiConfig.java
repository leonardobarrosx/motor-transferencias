package dev.leonardobarros.motor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    OpenAPI motorOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Motor de Transferências Financeiras")
                .description("Simulador de Pix / core bancário: transferências ACID, idempotentes e seguras sob concorrência.")
                .version("v1")
                .license(new License().name("MIT")));
    }
}
