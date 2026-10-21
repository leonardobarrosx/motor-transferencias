package dev.leonardobarros.motor.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.leonardobarros.motor.account.Account;
import dev.leonardobarros.motor.account.AccountRepository;
import dev.leonardobarros.motor.support.Accounts;
import dev.leonardobarros.motor.support.PostgresContainerConfig;

/**
 * O motor sob carga concorrente, contra Postgres real. Cada cenário dispara as
 * requisições juntas (latch) para maximizar a disputa pelos mesmos locks.
 */
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=20")
@Import(PostgresContainerConfig.class)
class TransferConcurrencyIT {

    private static final int THREADS = 16;

    @Autowired TransferService transfers;
    @Autowired AccountRepository accounts;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE ledger_entries, transfers, accounts");
    }

    @Test
    void concurrentDebitsNeverOverdrawTheAccount() throws Exception {
        Account payer = accounts.save(Accounts.active("300.00"));
        Account payee = accounts.save(Accounts.active("0.00"));
        int attempts = 50;

        List<TransferResult> results = runConcurrently(attempts, i -> () -> transfers.execute(UUID.randomUUID(),
                new TransferCommand(payer.getId(), payee.getPixKey(), new BigDecimal("10.00"), "lote " + i)));

        long completed = results.stream().filter(r -> r.transfer().status() == TransferStatus.COMPLETED).count();
        assertThat(completed).isEqualTo(30);
        assertThat(results).filteredOn(r -> r.transfer().status() == TransferStatus.REJECTED)
                .hasSize(20)
                .allSatisfy(r -> assertThat(r.transfer().rejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_FUNDS));

        assertThat(balanceOf(payer)).isEqualByComparingTo("0.00");
        assertThat(balanceOf(payee)).isEqualByComparingTo("300.00");
        assertLedgerExplainsEveryBalance(payer, payee);
    }

    @Test
    void opposingTransfersDoNotDeadlockAndConserveMoney() throws Exception {
        Account a = accounts.save(Accounts.active("1000.00"));
        Account b = accounts.save(Accounts.active("1000.00"));
        int attempts = 60;

        // metade A→B, metade B→A: sem ordem de lock consistente isto termina em deadlock
        List<TransferResult> results = runConcurrently(attempts, i -> () -> {
            boolean forward = i % 2 == 0;
            Account from = forward ? a : b;
            Account to = forward ? b : a;
            return transfers.execute(UUID.randomUUID(),
                    new TransferCommand(from.getId(), to.getPixKey(), new BigDecimal("7.50"), null));
        });

        assertThat(results).allSatisfy(r -> assertThat(r.transfer().status()).isEqualTo(TransferStatus.COMPLETED));
        assertThat(balanceOf(a)).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(b)).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(a).add(balanceOf(b))).isEqualByComparingTo("2000.00");
        assertLedgerExplainsEveryBalance(a, b);
    }

    @Test
    void sameIdempotencyKeyFiredConcurrentlyMovesMoneyOnce() throws Exception {
        Account payer = accounts.save(Accounts.active("100.00"));
        Account payee = accounts.save(Accounts.active("0.00"));
        UUID key = UUID.randomUUID();
        TransferCommand command = new TransferCommand(payer.getId(), payee.getPixKey(), new BigDecimal("40.00"), "pix");

        List<TransferResult> results = runConcurrently(20, i -> () -> transfers.execute(key, command));

        assertThat(results).extracting(r -> r.transfer().id()).containsOnly(results.getFirst().transfer().id());
        assertThat(results).filteredOn(r -> !r.replayed()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transfers", Long.class)).isEqualTo(1);
        assertThat(balanceOf(payer)).isEqualByComparingTo("60.00");
        assertThat(balanceOf(payee)).isEqualByComparingTo("40.00");
        assertLedgerExplainsEveryBalance(payer, payee);
    }

    @Test
    void rejectionIsIdempotentEvenAfterTheBalanceChanges() {
        Account payer = accounts.save(Accounts.active("10.00"));
        Account payee = accounts.save(Accounts.active("500.00"));
        UUID key = UUID.randomUUID();
        TransferCommand command = new TransferCommand(payer.getId(), payee.getPixKey(), new BigDecimal("50.00"), null);

        TransferResult first = transfers.execute(key, command);
        // a conta recebe dinheiro: agora teria saldo, mas o pedido original já foi decidido
        transfers.execute(UUID.randomUUID(),
                new TransferCommand(payee.getId(), payer.getPixKey(), new BigDecimal("100.00"), null));
        TransferResult retry = transfers.execute(key, command);

        assertThat(first.transfer().status()).isEqualTo(TransferStatus.REJECTED);
        assertThat(retry.replayed()).isTrue();
        assertThat(retry.transfer().id()).isEqualTo(first.transfer().id());
        assertThat(retry.transfer().status()).isEqualTo(TransferStatus.REJECTED);
        assertThat(balanceOf(payer)).isEqualByComparingTo("110.00");
    }

    private BigDecimal balanceOf(Account account) {
        return accounts.findById(account.getId()).orElseThrow().getBalance();
    }

    /**
     * Invariante de partidas dobradas: o total debitado é igual ao creditado, cada
     * transferência concluída tem exatamente dois lançamentos, e o saldo de cada
     * conta é o saldo de abertura mais o movimento registrado no livro-razão.
     */
    private void assertLedgerExplainsEveryBalance(Account... openings) {
        BigDecimal debits = jdbc.queryForObject(
                "SELECT coalesce(sum(amount), 0) FROM ledger_entries WHERE type = 'DEBIT'", BigDecimal.class);
        BigDecimal credits = jdbc.queryForObject(
                "SELECT coalesce(sum(amount), 0) FROM ledger_entries WHERE type = 'CREDIT'", BigDecimal.class);
        assertThat(debits).isEqualByComparingTo(credits);

        Long completed = jdbc.queryForObject("SELECT count(*) FROM transfers WHERE status = 'COMPLETED'", Long.class);
        Long entries = jdbc.queryForObject("SELECT count(*) FROM ledger_entries", Long.class);
        assertThat(entries).isEqualTo(completed * 2);

        for (Account opening : openings) {
            BigDecimal movement = jdbc.queryForObject("""
                    SELECT coalesce(sum(CASE WHEN type = 'CREDIT' THEN amount ELSE -amount END), 0)
                    FROM ledger_entries WHERE account_id = ?
                    """, BigDecimal.class, opening.getId());
            assertThat(balanceOf(opening)).isEqualByComparingTo(opening.getBalance().add(movement));
        }
    }

    private static <T> List<T> runConcurrently(int tasks, IntFunction<Callable<T>> factory)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < tasks; i++) {
                Callable<T> task = factory.apply(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }
}
