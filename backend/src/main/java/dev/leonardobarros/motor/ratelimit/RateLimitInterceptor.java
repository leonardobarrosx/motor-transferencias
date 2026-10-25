package dev.leonardobarros.motor.ratelimit;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Freia as duas rotas que mais atraem abuso:
 * <ul>
 *   <li><b>login</b>, por IP: tentativa de senha em massa;</li>
 *   <li><b>consulta de chave Pix</b>, por conta: varredura de chaves para colher nome e
 *       CPF mascarado de terceiros, que o Banco Central exige que os participantes impeçam.</li>
 * </ul>
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    public static final String LOGIN = "/api/v1/auth/login";
    public static final String PIX_KEY_LOOKUP = "/api/v1/pix/keys/**";

    private final TokenBucketLimiter login;
    private final TokenBucketLimiter pixKeyLookup;

    @Autowired
    RateLimitInterceptor(RateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    RateLimitInterceptor(RateLimitProperties properties, Clock clock) {
        this.login = new TokenBucketLimiter(properties.login(), clock);
        this.pixKeyLookup = new TokenBucketLimiter(properties.pixKeyLookup(), clock);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Duration wait = request.getRequestURI().endsWith(LOGIN)
                ? login.tryAcquire(request.getRemoteAddr())
                : pixKeyLookup.tryAcquire(callerOf(request));
        if (!wait.isZero()) {
            throw new RateLimitedException(wait);
        }
        return true;
    }

    /** A conta do token; sem token a requisição nem chegaria aqui, mas o IP serve de reserva. */
    private static String callerOf(HttpServletRequest request) {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
            return token.getToken().getClaimAsString("account_id");
        }
        return request.getRemoteAddr();
    }
}
