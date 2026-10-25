package dev.leonardobarros.motor.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.leonardobarros.motor.account.AccountRepository;

@Service
public class AuthService {

    /**
     * Hash de uma senha qualquer. Quando o e-mail não existe, a senha é comparada
     * com ele mesmo assim, para a resposta levar o mesmo tempo nos dois casos e
     * o tempo de resposta não revelar quais e-mails têm conta.
     */
    private static final String TIMING_DUMMY_HASH = "$2a$10$qjgaAvtRye0YALft/Bk7Cupwmeacr.4r6ybHk.u4/eYLKqIxjbUCK";

    private final UserRepository users;
    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    AuthService(UserRepository users, AccountRepository accounts, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = users.findByEmail(request.email().strip()).orElse(null);
        String hash = user == null ? TIMING_DUMMY_HASH : user.getPasswordHash();
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !matches) {
            throw new InvalidCredentialsException();
        }
        String holderName = accounts.findById(user.getAccountId()).orElseThrow().getHolderName();
        return new LoginResponse(tokens.issue(user), "Bearer", tokens.ttlSeconds(), holderName);
    }
}
