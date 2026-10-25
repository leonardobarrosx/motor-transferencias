package dev.leonardobarros.motor.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import dev.leonardobarros.motor.account.Account;
import dev.leonardobarros.motor.account.AccountRepository;
import dev.leonardobarros.motor.support.Accounts;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository users;
    @Mock AccountRepository accounts;
    @Mock PasswordEncoder passwordEncoder;
    @Mock TokenService tokens;
    @InjectMocks AuthService service;

    private final Account account = Accounts.active("100.00");
    private final User user = new User(UUID.randomUUID(), "ana@motor.dev", "$2a$hash", account.getId(), Instant.now());

    @Test
    void validCredentialsReturnAToken() {
        when(users.findByEmail("ana@motor.dev")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("motor123", "$2a$hash")).thenReturn(true);
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        when(tokens.issue(user)).thenReturn("jwt");
        when(tokens.ttlSeconds()).thenReturn(1800L);

        LoginResponse response = service.login(new LoginRequest(" ana@motor.dev ", "motor123"));

        assertThat(response.accessToken()).isEqualTo("jwt");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(1800);
        assertThat(response.holderName()).isEqualTo(account.getHolderName());
    }

    @Test
    void wrongPasswordIsRefused() {
        when(users.findByEmail("ana@motor.dev")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("errada", "$2a$hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("ana@motor.dev", "errada")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(tokens, never()).issue(user);
    }

    @Test
    void unknownEmailGetsTheSameAnswerAndStillPaysTheHashCost() {
        when(users.findByEmail("ninguem@motor.dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("ninguem@motor.dev", "motor123")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("E-mail ou senha incorretos.");
        // compara com um hash fictício: o tempo de resposta não denuncia se o e-mail existe
        verify(passwordEncoder).matches(eq("motor123"), anyString());
    }
}
