package dev.leonardobarros.motor.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import dev.leonardobarros.motor.shared.Money;

/**
 * Conta corrente. O saldo só muda por {@link #debit} e {@link #credit}, e as duas
 * só são chamadas pelo motor com a linha travada (SELECT ... FOR UPDATE).
 * O {@code @Version} é uma segunda trava: se algum caminho futuro esquecer o lock
 * pessimista, a escrita concorrente falha em vez de sobrescrever o saldo.
 */
@Entity
@Table(name = "accounts")
public class Account {

    public static final BigDecimal DEFAULT_DAYTIME_LIMIT = new BigDecimal("5000.00");
    public static final BigDecimal DEFAULT_NIGHTTIME_LIMIT = new BigDecimal("1000.00");

    @Id
    private UUID id;

    @Column(name = "holder_name", nullable = false, length = 120)
    private String holderName;

    @Column(nullable = false, length = 14, unique = true)
    private String document;

    @Column(nullable = false, length = 4)
    private String branch;

    @Column(nullable = false, length = 12, unique = true)
    private String number;

    @Column(name = "pix_key", nullable = false, length = 77, unique = true)
    private String pixKey;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountStatus status;

    /** Teto de Pix enviados entre 06h e 20h (horário de Brasília). */
    @Column(name = "daytime_pix_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal daytimePixLimit;

    /** Teto de Pix enviados entre 20h e 06h: menor, porque é quando mais acontece golpe. */
    @Column(name = "nighttime_pix_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal nighttimePixLimit;

    @Version
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, insertable = false, updatable = false)
    private AccountType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Account() {
        // JPA
    }

    public Account(UUID id, String holderName, String document, String branch, String number,
                   String pixKey, BigDecimal balance, AccountStatus status, Instant now) {
        this.id = Objects.requireNonNull(id);
        this.holderName = Objects.requireNonNull(holderName);
        this.document = Objects.requireNonNull(document);
        this.branch = Objects.requireNonNull(branch);
        this.number = Objects.requireNonNull(number);
        this.pixKey = Objects.requireNonNull(pixKey);
        this.balance = Money.requireValid(balance);
        this.status = Objects.requireNonNull(status);
        this.daytimePixLimit = DEFAULT_DAYTIME_LIMIT;
        this.nighttimePixLimit = DEFAULT_NIGHTTIME_LIMIT;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    /** Saldo disponível: o saldo menos o que está reservado para Pix ainda pendentes no SPI. */
    public boolean canDebit(BigDecimal amount, BigDecimal onHold) {
        return balance.subtract(onHold).compareTo(amount) >= 0;
    }

    public boolean canDebit(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    public void debit(BigDecimal amount, Instant now) {
        Money.requirePositive(amount);
        requireActive();
        if (!canDebit(amount)) {
            throw new IllegalStateException("Saldo insuficiente na conta " + id);
        }
        balance = balance.subtract(amount);
        updatedAt = now;
    }

    public void credit(BigDecimal amount, Instant now) {
        Money.requirePositive(amount);
        requireActive();
        balance = balance.add(amount);
        updatedAt = now;
    }

    private void requireActive() {
        if (!isActive()) {
            throw new IllegalStateException("Conta " + id + " não está ativa");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getHolderName() {
        return holderName;
    }

    public String getDocument() {
        return document;
    }

    public String getBranch() {
        return branch;
    }

    public String getNumber() {
        return number;
    }

    public String getPixKey() {
        return pixKey;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public BigDecimal getDaytimePixLimit() {
        return daytimePixLimit;
    }

    public BigDecimal getNighttimePixLimit() {
        return nighttimePixLimit;
    }

    /** Só para testes e ajustes de demonstração; num banco, aumento de limite tem carência. */
    public void changePixLimits(BigDecimal daytime, BigDecimal nighttime) {
        this.daytimePixLimit = Money.requirePositive(daytime);
        this.nighttimePixLimit = Money.requirePositive(nighttime);
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
