package dev.leonardobarros.motor.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.leonardobarros.motor.ledger.EntryType;
import dev.leonardobarros.motor.ledger.StatementLine;
import dev.leonardobarros.motor.support.Accounts;
import dev.leonardobarros.motor.support.PostgresContainerConfig;
import dev.leonardobarros.motor.transfer.TransferCommand;
import dev.leonardobarros.motor.transfer.TransferService;

@SpringBootTest
@Import(PostgresContainerConfig.class)
class AccountStatementIT {

    @Autowired AccountService service;
    @Autowired AccountRepository accounts;
    @Autowired TransferService transfers;
    @Autowired JdbcTemplate jdbc;

    private Account leo;
    private Account ana;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE ledger_entries, transfers, users, accounts");
        leo = accounts.save(Accounts.active("1000.00"));
        ana = accounts.save(Accounts.active("200.00"));
    }

    private void pix(Account from, Account to, String amount, String description) {
        transfers.execute(UUID.randomUUID(),
                new TransferCommand(from.getId(), to.getPixKey(), new BigDecimal(amount), description));
    }

    @Test
    void statementListsNewestFirstWithCounterpartyAndRunningBalance() {
        pix(leo, ana, "100.00", "almoço");
        pix(ana, leo, "30.00", "devolução");
        pix(leo, ana, "5000.00", "recusada");   // sem saldo: não entra no extrato

        Page<StatementLine> page = service.statement(leo.getId(), null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        StatementLine newest = page.getContent().get(0);
        StatementLine oldest = page.getContent().get(1);
        assertThat(newest.type()).isEqualTo(EntryType.CREDIT);
        assertThat(newest.counterpartyName()).isEqualTo(ana.getHolderName());
        assertThat(newest.description()).isEqualTo("devolução");
        assertThat(newest.balanceAfter()).isEqualByComparingTo("930.00");
        assertThat(newest.endToEndId()).hasSize(32);
        assertThat(oldest.type()).isEqualTo(EntryType.DEBIT);
        assertThat(oldest.balanceAfter()).isEqualByComparingTo("900.00");
    }

    @Test
    void filtersByEntryTypeAndListsRecentContacts() {
        Account carlos = accounts.save(Accounts.active("50.00"));
        pix(leo, ana, "10.00", null);
        pix(carlos, leo, "5.00", null);
        pix(leo, ana, "20.00", null);

        Page<StatementLine> credits = service.statement(leo.getId(), EntryType.CREDIT, PageRequest.of(0, 10));
        List<ContactView> contacts = service.recentContacts(leo.getId());

        assertThat(credits.getContent()).singleElement()
                .satisfies(line -> assertThat(line.counterpartyName()).isEqualTo(carlos.getHolderName()));
        assertThat(contacts).extracting(ContactView::holderName)
                .containsExactly(ana.getHolderName(), carlos.getHolderName());
    }

    @Test
    void accountViewCarriesTheMonthToDateMovement() {
        pix(leo, ana, "100.00", null);
        pix(leo, ana, "50.50", null);
        pix(ana, leo, "20.00", null);

        AccountView view = service.get(leo.getId());

        assertThat(view.balance()).isEqualByComparingTo("869.50");
        assertThat(view.monthToDate().outflow()).isEqualByComparingTo("150.50");
        assertThat(view.monthToDate().inflow()).isEqualByComparingTo("20.00");
        assertThat(view.monthToDate().transfers()).isEqualTo(3);
        assertThat(view.document()).startsWith("***.").endsWith("-**");
    }

    @Test
    void unknownAccount() {
        assertThatThrownBy(() -> service.statement(UUID.randomUUID(), null, PageRequest.of(0, 10)))
                .isInstanceOf(AccountNotFoundException.class);
    }
}
