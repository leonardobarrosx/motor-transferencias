package dev.leonardobarros.motor.shared.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Erro de regra de negócio. Carrega o status HTTP e um código estável que o
 * cliente pode tratar sem depender do texto da mensagem.
 */
public abstract class DomainException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected DomainException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    /** Campos extras anexados ao Problem Details. */
    public Map<String, Object> details() {
        return Map.of();
    }
}
