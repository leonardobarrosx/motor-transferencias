package dev.leonardobarros.motor.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import dev.leonardobarros.motor.auth.CurrentAccountResolver;

@Configuration(proxyBeanMethods = false)
class WebConfig implements WebMvcConfigurer {

    private final CurrentAccountResolver currentAccount;

    WebConfig(CurrentAccountResolver currentAccount) {
        this.currentAccount = currentAccount;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentAccount);
    }
}
