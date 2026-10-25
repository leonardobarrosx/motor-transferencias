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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private TransferKind kind;

    /** Só em devoluções: o Pix que está sendo devolvido. */
    @Column(name = "original_transfer_id", updatable = false)
    private UUID originalTransferId;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_reason", length = 24, updatable = false)
    private RefundReason refundReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transfer() {
        // JPA
    }

    private Transfer(UUID idempotencyKey, String requestHash, UUID sourceAccountId, UUID targetAccountId,
                     BigDecimal amount, String description, TransferStatus status,
                     RejectionReason rejectionReason, Instant now) {
        this(TransferKind.PIX, null, null, idempotencyKey, requestHash, sourceAccountId, targetAccountId,
                amount, description, status, rejectionReason, now);
    }

    private Transfer(TransferKind kind, UUID originalTransferId, RefundReason refundReason,
                     UUID idempotencyKey, String requestHash, UUID sourceAccountId, UUID targetAccountId,
                     BigDecimal amount, String description, TransferStatus status,
                     RejectionReason rejectionReason, Instant now) {
        this.id = UUID.randomUUID();
        this.kind = kind;
        this.originalTransferId = originalTransferId;
        this.refundReason = refundReason;
        this.endToEndId = kind == TransferKind.REFUND ? EndToEndId.generateReturn(now) : EndToEndId.generate(now);
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

    /** Devolução: sai de quem recebeu o original e volta para quem pagou. */
    public static Transfer refund(UUID idempotencyKey, String requestHash, RefundCommand command, Transfer original,
                                  RejectionReason rejection, Instant now) {
        return new Transfer(TransferKind.REFUND, original.getId(), command.reason(), idempotencyKey, requestHash,
                original.getTargetAccountId(), original.getSourceAccountId(), command.amount(),
                "Devolução de Pix", rejection == null ? TransferStatus.COMPLETED : TransferStatus.REJECTED,
                rejection, now);
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

    public TransferKind getKind() {
        return kind;
    }

    public UUID getOriginalTransferId() {
        return originalTransferId;
    }

    public RefundReason getRefundReason() {
        return refundReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
