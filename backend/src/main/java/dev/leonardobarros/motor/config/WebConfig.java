package dev.leonardobarros.motor.config;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import dev.leonardobarros.motor.auth.CurrentAccountResolver;
import dev.leonardobarros.motor.ratelimit.RateLimitInterceptor;
import dev.leonardobarros.motor.ratelimit.RateLimitProperties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
class WebConfig implements WebMvcConfigurer {

    private final CurrentAccountResolver currentAccount;
    private final RateLimitInterceptor rateLimit;

    WebConfig(CurrentAccountResolver currentAccount, RateLimitInterceptor rateLimit) {
        this.currentAccount = currentAccount;
        this.rateLimit = rateLimit;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimit).addPathPatterns(RateLimitInterceptor.LOGIN, RateLimitInterceptor.PIX_KEY_LOOKUP);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentAccount);
    }
}
