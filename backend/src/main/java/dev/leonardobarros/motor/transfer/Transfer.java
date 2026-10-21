package dev.leonardobarros.motor.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import dev.leonardobarros.motor.shared.persistence.AssignedIdEntity;

/**
 * Ordem de transferência já decidida: concluída ou recusada. É gravada junto com
 * a Idempotency-Key e o hash do pedido, e nunca muda depois disso.
 */
@Entity
@Table(name = "transfers")
public class Transfer extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "end_to_end_id", nullable = false, length = 32, unique = true, updatable = false)
    private String endToEndId;

    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false)
    private UUID idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Column(name = "source_account_id", nullable = false, updatable = false)
    private UUID sourceAccountId;

    @Column(name = "target_account_id", nullable = false, updatable = false)
    private UUID targetAccountId;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(length = 140, updatable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private TransferStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason", length = 32, updatable = false)
    private RejectionReason rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transfer() {
        // JPA
    }

    private Transfer(UUID idempotencyKey, String requestHash, UUID sourceAccountId, UUID targetAccountId,
                     BigDecimal amount, String description, TransferStatus status,
                     RejectionReason rejectionReason, Instant now) {
        this.id = UUID.randomUUID();
        this.endToEndId = EndToEndId.generate(now);
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.amount = amount;
        this.description = description;
        this.status = status;
        this.rejectionReason = rejectionReason;
        this.createdAt = now;
    }

    public static Transfer completed(UUID idempotencyKey, String requestHash, TransferCommand command,
                                     UUID targetAccountId, Instant now) {
        return new Transfer(idempotencyKey, requestHash, command.sourceAccountId(), targetAccountId,
                command.amount(), command.description(), TransferStatus.COMPLETED, null, now);
    }

    public static Transfer rejected(UUID idempotencyKey, String requestHash, TransferCommand command,
                                    UUID targetAccountId, RejectionReason reason, Instant now) {
        return new Transfer(idempotencyKey, requestHash, command.sourceAccountId(), targetAccountId,
                command.amount(), command.description(), TransferStatus.REJECTED, reason, now);
    }

    public boolean isCompleted() {
        return status == TransferStatus.COMPLETED;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getEndToEndId() {
        return endToEndId;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getTargetAccountId() {
        return targetAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public RejectionReason getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
