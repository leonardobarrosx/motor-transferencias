package dev.leonardobarros.motor.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.leonardobarros.motor.account.Account;
import dev.leonardobarros.motor.account.AccountRepository;
import dev.leonardobarros.motor.support.Accounts;
import dev.leonardobarros.motor.support.PostgresContainerConfig;

/** Limite noturno e devoluções contra Postgres real, com o relógio parado às 20h30 de Brasília. */
@SpringBootTest
@Import({PostgresContainerConfig.class, LimitsAndRefundsIT.NightClock.class})
class LimitsAndRefundsIT {

    static final Instant NIGHT = Instant.parse("2026-10-25T23:30:00Z"); // 20h30 em Brasília

    @TestConfiguration(proxyBeanMethods = false)
    static class NightClock {
        @Bean
        @Primary
        Clock nightClock() {
            return Clock.fixed(NIGHT, ZoneOffset.UTC);
        }
    }

    @Autowired TransferService transfers;
    @Autowired AccountRepository accounts;
    @Autowired PixLimits limits;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE outbox_events, ledger_entries, transfers, users, accounts");
    }

    private TransferResult pix(Account from, Account to, String amount) {
        return transfers.execute(UUID.randomUUID(),
                new TransferCommand(from.getId(), to.getPixKey(), new BigDecimal(amount), null));
    }

    @Test
    void limiteNoturnoAguentaEnviosSimultaneos() throws Exception {
        Account payer = accounts.save(Accounts.active("10000.00"));   // saldo sobra; quem segura é o limite de R$ 1.000
        Account payee = accounts.save(Accounts.active("0.00"));

        List<TransferResult> results = concurrently(10, () -> pix(payer, payee, "150.00"));

        assertThat(results).filteredOn(r -> r.transfer().status() == TransferStatus.COMPLETED).hasSize(6);   // 900
        assertThat(results).filteredOn(r -> r.transfer().status() == TransferStatus.REJECTED)
                .hasSize(4)
                .allSatisfy(r -> assertThat(r.transfer().rejectionReason()).isEqualTo(RejectionReason.LIMIT_EXCEEDED));

        PixLimits.Snapshot snapshot = limits.snapshot(payer.getId());
        assertThat(snapshot.period()).isEqualTo(PixLimits.Period.NIGHTTIME);
        assertThat(snapshot.used()).isEqualByComparingTo("900.00");
        assertThat(snapshot.available()).isEqualByComparingTo("100.00");
    }

    @Test
    void devolucaoParcialVoltaParaQuemPagouEUsaIdD() {
        Account payer = accounts.save(Accounts.active("500.00"));
        Account payee = accounts.save(Accounts.active("0.00"));
        TransferView original = pix(payer, payee, "200.00").transfer();

        TransferResult refund = transfers.refund(UUID.randomUUID(),
                new RefundCommand(payee.getId(), original.id(), new BigDecimal("80.00"), RefundReason.CUSTOMER_REQUEST));

        assertThat(refund.transfer().status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(refund.transfer().kind()).isEqualTo(TransferKind.REFUND);
        assertThat(refund.transfer().endToEndId()).startsWith("D");
        assertThat(refund.transfer().payee().accountId()).isEqualTo(payer.getId());
        assertThat(balance(payer)).isEqualByComparingTo("380.00");
        assertThat(balance(payee)).isEqualByComparingTo("120.00");
        assertThat(transfers.find(original.id(), payer.getId()).refundedAmount()).isEqualByComparingTo("80.00");
        // devolução não consome o limite de quem devolve
        assertThat(limits.snapshot(payee.getId()).used()).isEqualByComparingTo("0.00");
    }

    @Test
    void devolucoesSimultaneasNuncaPassamDoValorOriginal() throws Exception {
        Account payer = accounts.save(Accounts.active("500.00"));
        Account payee = accounts.save(Accounts.active("0.00"));
        TransferView original = pix(payer, payee, "100.00").transfer();

        List<Object> outcomes = concurrently(8, () -> {
            try {
                return transfers.refund(UUID.randomUUID(),
                        new RefundCommand(payee.getId(), original.id(), new BigDecimal("30.00"), null));
            } catch (RefundNotAllowedException e) {
                return e.code();
            }
        });

        assertThat(outcomes).filteredOn(TransferResult.class::isInstance).hasSize(3);   // 3 × 30 = 90 ≤ 100
        assertThat(outcomes).filteredOn("REFUND_AMOUNT_EXCEEDED"::equals).hasSize(5);
        assertThat(balance(payer)).isEqualByComparingTo("490.00");
        assertThat(balance(payee)).isEqualByComparingTo("10.00");
    }

    @Test
    void soQuemRecebeuPodeDevolver() {
        Account payer = accounts.save(Accounts.active("500.00"));
        Account payee = accounts.save(Accounts.active("0.00"));
        Account outsider = accounts.save(Accounts.active("0.00"));
        TransferView original = pix(payer, payee, "50.00").transfer();

        assertThatThrownBy(() -> transfers.refund(UUID.randomUUID(),
                new RefundCommand(payer.getId(), original.id(), BigDecimal.TEN, null)))
                .isInstanceOf(RefundNotAllowedException.class);
        assertThatThrownBy(() -> transfers.refund(UUID.randomUUID(),
                new RefundCommand(outsider.getId(), original.id(), BigDecimal.TEN, null)))
                .isInstanceOf(TransferNotFoundException.class);
    }

    @Test
    void devolucaoTambemEhIdempotente() {
        Account payer = accounts.save(Accounts.active("500.00"));
        Account payee = accounts.save(Accounts.active("0.00"));
        TransferView original = pix(payer, payee, "50.00").transfer();
        UUID key = UUID.randomUUID();
        RefundCommand command = new RefundCommand(payee.getId(), original.id(), new BigDecimal("50.00"), null);

        TransferResult first = transfers.refund(key, command);
        TransferResult again = transfers.refund(key, command);

        assertThat(again.replayed()).isTrue();
        assertThat(again.transfer().id()).isEqualTo(first.transfer().id());
        assertThat(balance(payer)).isEqualByComparingTo("500.00");
    }

    private BigDecimal balance(Account account) {
        return accounts.findById(account.getId()).orElseThrow().getBalance();
    }

    private static <T> List<T> concurrently(int tasks, Callable<T> task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(tasks)) {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < tasks; i++) {
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
