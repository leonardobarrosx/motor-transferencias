package dev.leonardobarros.motor.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import dev.leonardobarros.motor.account.Account;
import dev.leonardobarros.motor.shared.persistence.AssignedIdEntity;

/**
 * Lançamento do livro-razão (partidas dobradas). Toda transferência concluída
 * gera um DEBIT na origem e um CREDIT no destino, com o mesmo valor; o extrato
 * é lido daqui, e a soma dos lançamentos explica o saldo de cada conta.
 * Imutável: lançamento errado se corrige com estorno, nunca com UPDATE.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "counterparty_account_id", nullable = false, updatable = false)
    private UUID counterpartyAccountId;

    @Column(name = "transfer_id", nullable = false, updatable = false)
    private UUID transferId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8, updatable = false)
    private EntryType type;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
        // JPA
    }

    private LedgerEntry(Account account, UUID counterpartyAccountId, UUID transferId,
                        EntryType type, BigDecimal amount, Instant now) {
        this.id = UUID.randomUUID();
        this.accountId = account.getId();
        this.counterpartyAccountId = counterpartyAccountId;
        this.transferId = transferId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = account.getBalance();
        this.createdAt = now;
    }

    /** Chamar depois de {@code account.debit(...)}: o saldo registrado é o saldo resultante. */
    public static LedgerEntry debit(Account account, UUID counterpartyAccountId, UUID transferId,
                                    BigDecimal amount, Instant now) {
        return new LedgerEntry(account, counterpartyAccountId, transferId, EntryType.DEBIT, amount, now);
    }

    /** Chamar depois de {@code account.credit(...)}: o saldo registrado é o saldo resultante. */
    public static LedgerEntry credit(Account account, UUID counterpartyAccountId, UUID transferId,
                                     BigDecimal amount, Instant now) {
        return new LedgerEntry(account, counterpartyAccountId, transferId, EntryType.CREDIT, amount, now);
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getCounterpartyAccountId() {
        return counterpartyAccountId;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public EntryType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
