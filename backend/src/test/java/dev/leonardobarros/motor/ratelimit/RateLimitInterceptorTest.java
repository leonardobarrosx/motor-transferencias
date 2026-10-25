package dev.leonardobarros.motor.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class RateLimitInterceptorTest {

    /** Relógio que o teste avança à mão. */
    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-25T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private RateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        var properties = new RateLimitProperties(
                new RateLimitProperties.Rule(3, Duration.ofMinutes(1)),
                new RateLimitProperties.Rule(2, Duration.ofMinutes(1)));
        interceptor = new RateLimitInterceptor(properties, clock);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private boolean login(String ip) {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(ip);
        return interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
    }

    private boolean lookupKeyAs(String accountId) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("t")
                .header("alg", "HS256").claim("account_id", accountId).build()));
        return interceptor.preHandle(new MockHttpServletRequest("GET", "/api/v1/pix/keys/ana@motor.dev"),
                new MockHttpServletResponse(), new Object());
    }

    @Test
    void loginIsLimitedPerIpAndSaysWhenToComeBack() {
        assertThat(login("10.0.0.1")).isTrue();
        assertThat(login("10.0.0.1")).isTrue();
        assertThat(login("10.0.0.1")).isTrue();

        assertThatThrownBy(() -> login("10.0.0.1"))
                .isInstanceOfSatisfying(RateLimitedException.class,
                        // 3 fichas por minuto: uma volta a cada 20 s
                        ex -> assertThat(ex.retryAfterSeconds()).isEqualTo(20));
        // outro IP tem o próprio balde
        assertThat(login("10.0.0.2")).isTrue();
    }

    @Test
    void tokensComeBackOverTime() {
        login("10.0.0.1");
        login("10.0.0.1");
        login("10.0.0.1");

        clock.now = clock.now.plusSeconds(20);

        assertThat(login("10.0.0.1")).isTrue();
        assertThatThrownBy(() -> login("10.0.0.1")).isInstanceOf(RateLimitedException.class);
    }

    @Test
    void pixKeyLookupIsLimitedPerAccountNotPerIp() {
        assertThat(lookupKeyAs("conta-a")).isTrue();
        assertThat(lookupKeyAs("conta-a")).isTrue();

        assertThatThrownBy(() -> lookupKeyAs("conta-a")).isInstanceOf(RateLimitedException.class);
        assertThat(lookupKeyAs("conta-b")).isTrue();
    }
}
