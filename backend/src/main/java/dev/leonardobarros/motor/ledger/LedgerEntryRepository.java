package dev.leonardobarros.motor.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.leonardobarros.motor.account.ContactView;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    @Query(value = """
            select new dev.leonardobarros.motor.ledger.StatementLine(
                e.id, e.transferId, t.endToEndId, e.type, e.amount, e.balanceAfter,
                cp.holderName, t.description, e.createdAt)
            from LedgerEntry e
            join Transfer t on t.id = e.transferId
            join Account cp on cp.id = e.counterpartyAccountId
            where e.accountId = :accountId
            order by e.createdAt desc, e.id desc
            """,
            countQuery = "select count(e) from LedgerEntry e where e.accountId = :accountId")
    Page<StatementLine> findStatement(@Param("accountId") UUID accountId, Pageable pageable);

    @Query(value = """
            select new dev.leonardobarros.motor.ledger.StatementLine(
                e.id, e.transferId, t.endToEndId, e.type, e.amount, e.balanceAfter,
                cp.holderName, t.description, e.createdAt)
            from LedgerEntry e
            join Transfer t on t.id = e.transferId
            join Account cp on cp.id = e.counterpartyAccountId
            where e.accountId = :accountId and e.type = :type
            order by e.createdAt desc, e.id desc
            """,
            countQuery = "select count(e) from LedgerEntry e where e.accountId = :accountId and e.type = :type")
    Page<StatementLine> findStatementByType(@Param("accountId") UUID accountId, @Param("type") EntryType type,
                                            Pageable pageable);

    /** Uma linha por contraparte, da mais recente para a mais antiga. */
    @Query("""
            select new dev.leonardobarros.motor.account.ContactView(cp.holderName, cp.pixKey, max(e.createdAt))
            from LedgerEntry e
            join Account cp on cp.id = e.counterpartyAccountId
            where e.accountId = :accountId
            group by cp.id, cp.holderName, cp.pixKey
            order by max(e.createdAt) desc
            """)
    List<ContactView> findRecentCounterparties(@Param("accountId") UUID accountId, Pageable pageable);

    @Query("""
            select e.type as type, sum(e.amount) as total, count(e) as entries
            from LedgerEntry e
            where e.accountId = :accountId and e.createdAt >= :since
            group by e.type
            """)
    List<TypeTotal> totalsSince(@Param("accountId") UUID accountId, @Param("since") Instant since);

    List<LedgerEntry> findByTransferId(UUID transferId);

    interface TypeTotal {
        EntryType getType();

        BigDecimal getTotal();

        long getEntries();
    }
}
