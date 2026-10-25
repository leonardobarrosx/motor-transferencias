package dev.leonardobarros.motor.account;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.leonardobarros.motor.ledger.EntryType;
import dev.leonardobarros.motor.ledger.LedgerEntryRepository;
import dev.leonardobarros.motor.ledger.LedgerEntryRepository.TypeTotal;
import dev.leonardobarros.motor.ledger.StatementLine;

@Service
@Transactional(readOnly = true)
public class AccountService {

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final int RECENT_CONTACTS = 6;

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final Clock clock;

    AccountService(AccountRepository accounts, LedgerEntryRepository ledger, Clock clock) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.clock = clock;
    }

    public AccountView get(UUID accountId) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        return toView(account, monthToDate(accountId));
    }

    public Page<StatementLine> statement(UUID accountId, EntryType type, Pageable pageable) {
        if (!accounts.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        return type == null
                ? ledger.findStatement(accountId, pageable)
                : ledger.findStatementByType(accountId, type, pageable);
    }

    public List<ContactView> recentContacts(UUID accountId) {
        return ledger.findRecentCounterparties(accountId, PageRequest.of(0, RECENT_CONTACTS));
    }

    private AccountView.MonthToDate monthToDate(UUID accountId) {
        Instant startOfMonth = LocalDate.now(clock.withZone(BRASILIA))
                .withDayOfMonth(1)
                .atStartOfDay(BRASILIA)
                .toInstant();
        BigDecimal inflow = ZERO;
        BigDecimal outflow = ZERO;
        long entries = 0;
        for (TypeTotal total : ledger.totalsSince(accountId, startOfMonth)) {
            if (total.getType() == EntryType.CREDIT) {
                inflow = total.getTotal();
            } else {
                outflow = total.getTotal();
            }
            entries += total.getEntries();
        }
        return new AccountView.MonthToDate(inflow, outflow, entries);
    }

    private static AccountView toView(Account account, AccountView.MonthToDate monthToDate) {
        return new AccountView(
                account.getId(),
                account.getHolderName(),
                AccountView.mask(account.getDocument()),
                account.getBranch(),
                account.getNumber(),
                account.getPixKey(),
                account.getBalance(),
                account.getStatus(),
                monthToDate);
    }
}
