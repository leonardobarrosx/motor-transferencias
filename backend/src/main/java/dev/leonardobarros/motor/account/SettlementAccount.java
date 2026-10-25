package dev.leonardobarros.motor.account;

import java.util.UUID;

/**
 * A conta de liquidação: espelha a conta PI do banco no Banco Central, por onde
 * passa todo Pix trocado com outras instituições. Criada pela migração V7.
 *
 * <p>É uma conta "quente": todo Pix externo liquidado a trava. Com volume real,
 * a saída seria dividi-la em várias subcontas e escolher uma por hash, ou lançar
 * o crédito sem atualizar o saldo na hora (saldo calculado pelo livro-razão).
 */
public final class SettlementAccount {

    public static final UUID ID = UUID.fromString("00000000-0000-4000-8000-00000000c0de");

    private SettlementAccount() {
    }
}
