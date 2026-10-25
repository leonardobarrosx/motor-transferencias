package dev.leonardobarros.integracoes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IntegracoesApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntegracoesApplication.class, args);
    }
}
