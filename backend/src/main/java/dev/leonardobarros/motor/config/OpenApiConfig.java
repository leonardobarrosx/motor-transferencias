package dev.leonardobarros.motor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    OpenAPI motorOpenApi() {
        // "Authorize" no Swagger: cole o accessToken devolvido por /api/v1/auth/login
        SecurityScheme bearer = new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT");
        return new OpenAPI()
                .components(new Components().addSecuritySchemes("bearer", bearer))
                .info(new Info()
                .title("Motor de Transferências Financeiras")
                .description("Simulador de Pix / core bancário: transferências ACID, idempotentes e seguras sob concorrência.")
                .version("v1")
                .license(new License().name("MIT")));
    }
}
