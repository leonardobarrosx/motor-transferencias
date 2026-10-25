package dev.leonardobarros.motor.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByIdempotencyKey(UUID idempotencyKey);

    /** Quanto a conta já enviou em Pix (não conta devoluções) desde o início da janela de limite. */
    @Query("""
            select coalesce(sum(t.amount), 0) from Transfer t
            where t.sourceAccountId = :accountId and t.status = TransferStatus.COMPLETED
              and t.kind = TransferKind.PIX and t.createdAt >= :since
            """)
    BigDecimal sumPixSentSince(@Param("accountId") UUID accountId, @Param("since") Instant since);

    /** Quanto de um Pix já foi devolvido. */
    @Query("""
            select coalesce(sum(t.amount), 0) from Transfer t
            where t.originalTransferId = :originalId and t.status = TransferStatus.COMPLETED
            """)
    BigDecimal sumRefunded(@Param("originalId") UUID originalId);
}
