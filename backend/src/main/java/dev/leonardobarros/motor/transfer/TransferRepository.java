package dev.leonardobarros.motor.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findBySourceAccountIdAndIdempotencyKey(UUID sourceAccountId, UUID idempotencyKey);

    /** Quanto a conta já enviou em Pix (não conta devoluções) desde o início da janela de limite. */
    @Query("""
            select coalesce(sum(t.amount), 0) from Transfer t
            where t.sourceAccountId = :accountId
              and t.status in (TransferStatus.COMPLETED, TransferStatus.PENDING)
              and t.kind = TransferKind.PIX and t.createdAt >= :since
            """)
    BigDecimal sumPixSentSince(@Param("accountId") UUID accountId, @Param("since") Instant since);

    /** Quanto da conta está reservado em Pix para outros bancos ainda sem resposta do SPI. */
    @Query("""
            select coalesce(sum(t.amount), 0) from Transfer t
            where t.sourceAccountId = :accountId and t.status = TransferStatus.PENDING
            """)
    BigDecimal sumPendingFrom(@Param("accountId") UUID accountId);

    /** Só o pagador, sem carregar a entidade: ela precisa ser lida depois do lock, não antes. */
    @Query("select t.sourceAccountId from Transfer t where t.id = :id")
    Optional<UUID> findSourceAccountId(@Param("id") UUID id);

    /** Quanto de um Pix já foi devolvido. */
    @Query("""
            select coalesce(sum(t.amount), 0) from Transfer t
            where t.originalTransferId = :originalId and t.status = TransferStatus.COMPLETED
            """)
    BigDecimal sumRefunded(@Param("originalId") UUID originalId);
}
