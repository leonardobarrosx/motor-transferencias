package dev.leonardobarros.motor.shared.web;

public final class Headers {

    /** Enviado pelo cliente: identifica a intenção de transferir, não a requisição HTTP. */
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    /** Devolvido quando a resposta é a repetição de uma transferência já processada. */
    public static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private Headers() {
    }
}
