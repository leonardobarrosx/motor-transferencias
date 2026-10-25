package dev.leonardobarros.motor.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import dev.leonardobarros.motor.pix.ChaveExterna;
import dev.leonardobarros.motor.shared.persistence.AssignedIdEntity;

/**
 * Ordem de transferência, gravada junto com a Idempotency-Key e o hash do pedido.
 * Pix entre contas daqui nasce decidido (concluído ou recusado) e não muda mais.
 * Pix para outro banco nasce PENDING e muda uma única vez, quando o SPI responde.
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
    @Column(nullable = false, length = 16)
    private TransferStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason", length = 32)
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

    // Recebedor em outra instituição (dados do DICT). Nulos em Pix entre contas daqui.
    @Column(name = "payee_ispb", length = 8, updatable = false)
    private String payeeIspb;

    @Column(name = "payee_institution", length = 80, updatable = false)
    private String payeeInstitution;

    @Column(name = "payee_name", length = 120, updatable = false)
    private String payeeName;

    @Column(name = "payee_document", length = 14, updatable = false)
    private String payeeDocument;

    @Column(name = "payee_pix_key", length = 77, updatable = false)
    private String payeePixKey;

    @Column(name = "payee_branch", length = 4, updatable = false)
    private String payeeBranch;

    @Column(name = "payee_account_number", length = 12, updatable = false)
    private String payeeAccountNumber;

    /** Código ISO 20022 da recusa vinda do SPI (pacs.002 RJCT). */
    @Column(name = "spi_reason_code", length = 8)
    private String spiReasonCode;

    @Column(name = "settled_at")
    private Instant settledAt;

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

    /**
     * Pix para outra instituição. Aceito pelas regras daqui, vai PENDING para o SPI;
     * recusado aqui (saldo, limite, bloqueio), nem chega a sair.
     */
    public static Transfer toOtherInstitution(UUID idempotencyKey, String requestHash, TransferCommand command,
                                              UUID settlementAccountId, ChaveExterna payee,
                                              RejectionReason rejection, Instant now) {
        Transfer transfer = new Transfer(idempotencyKey, requestHash, command.sourceAccountId(), settlementAccountId,
                command.amount(), command.description(),
                rejection == null ? TransferStatus.PENDING : TransferStatus.REJECTED, rejection, now);
        transfer.payeeIspb = payee.ispb();
        transfer.payeeInstitution = payee.institution();
        transfer.payeeName = payee.holderName();
        transfer.payeeDocument = payee.document();
        transfer.payeePixKey = payee.pixKey();
        transfer.payeeBranch = payee.branch();
        transfer.payeeAccountNumber = payee.accountNumber();
        return transfer;
    }

    /** O SPI liquidou (pacs.002 ACSC). */
    void settle(Instant now) {
        requirePending();
        this.status = TransferStatus.COMPLETED;
        this.settledAt = now;
    }

    /** O SPI ou o banco recebedor recusou (pacs.002 RJCT): a reserva é liberada. */
    void rejectBySpi(String isoReasonCode, Instant now) {
        requirePending();
        this.status = TransferStatus.REJECTED;
        this.rejectionReason = RejectionReason.RECEIVER_REJECTED;
        this.spiReasonCode = isoReasonCode;
        this.settledAt = now;
    }

    private void requirePending() {
        if (status != TransferStatus.PENDING) {
            throw new IllegalStateException("Transferência " + id + " já decidida: " + status);
        }
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

    public boolean isPending() {
        return status == TransferStatus.PENDING;
    }

    /** O recebedor está em outra instituição. */
    public boolean isToOtherInstitution() {
        return payeeIspb != null;
    }

    /** O recebedor como o DICT o descreveu; vazio em Pix entre contas daqui. */
    public Optional<ChaveExterna> getExternalPayee() {
        return isToOtherInstitution()
                ? Optional.of(new ChaveExterna(payeePixKey, payeeName, payeeDocument, payeeIspb,
                        payeeInstitution, payeeBranch, payeeAccountNumber))
                : Optional.empty();
    }

    public String getSpiReasonCode() {
        return spiReasonCode;
    }

    public Instant getSettledAt() {
        return settledAt;
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
