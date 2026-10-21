package dev.leonardobarros.motor.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Linha do extrato: o lançamento com o nome da contraparte e a descrição da transferência.
 * Valores monetários saem como string decimal ("150.75"), como na API Pix do Banco Central:
 * número em JSON vira ponto flutuante no JavaScript.
 */
public record StatementLine(
        UUID entryId,
        UUID transferId,
        String endToEndId,
        EntryType type,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balanceAfter,
        String counterpartyName,
        String description,
        Instant createdAt) {
}
