package dev.leonardobarros.motor.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.leonardobarros.motor.account.Account;
import dev.leonardobarros.motor.account.AccountRepository;
import dev.leonardobarros.motor.account.SettlementAccount;
import dev.leonardobarros.motor.outbox.TransferEvent;
import dev.leonardobarros.motor.support.Accounts;
import dev.leonardobarros.motor.support.PostgresContainerConfig;

/**
 * Pix para outro banco, de ponta a ponta no motor: reserva, resposta do SPI,
 * liquidação ou compensação. O Kafka fica de fora: a resposta do SPI é entregue
 * direto ao processador, como o listener faria.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class ExternalPixIT {

    private static final String MARIA = "maria@bancoexemplo.dev";   // DICT simulado: Banco Exemplo

    @Autowired TransferService transfers;
    @Autowired TransferProcessor processor;
    @Autowired AccountRepository accounts;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE outbox_events, ledger_entries, transfers, users, accounts");
        jdbc.update("""
                INSERT INTO accounts (id, holder_name, document, branch, number, pix_key, balance, status, type,
                                      version, created_at, updated_at)
                VALUES (?, 'Liquidação Pix', '99999999000000', '0000', 'PI-99999999', 'interno:liquidacao-spi', 0,
                        'ACTIVE', 'SETTLEMENT', 0, now(), now())
                """, SettlementAccount.ID);
    }

    private TransferResult pixTo(Account from, String key, String amount) {
        return transfers.execute(UUID.randomUUID(), new TransferCommand(from.getId(), key, new BigDecimal(amount), null));
    }

    private boolean spiAnswers(TransferView transfer, String status, String reason) {
        return processor.applySpiResult(new SpiResult(UUID.randomUUID(), transfer.id(), transfer.endToEndId(),
                status, reason, Instant.now()));
    }

    private BigDecimal balanceOf(UUID id) {
        return accounts.findById(id).orElseThrow().getBalance();
    }

    private String lastEventType() {
        return jdbc.queryForObject("SELECT event_type FROM outbox_events ORDER BY created_at DESC LIMIT 1", String.class);
    }

    @Test
    void ficaPendenteReservandoOValorSemLancamento() {
        Account payer = accounts.save(Accounts.active("100.00"));

        TransferView pending = pixTo(payer, MARIA, "60.00").transfer();

        assertThat(pending.status()).isEqualTo(TransferStatus.PENDING);
        assertThat(pending.payee().holderName()).isEqualTo("Maria Oliveira");
        assertThat(pending.payee().institution()).isEqualTo("Banco Exemplo");
        assertThat(pending.payee().accountId()).isNull();
        assertThat(balanceOf(payer.getId())).isEqualByComparingTo("100.00");     // nada saiu ainda
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_entries", Long.class)).isZero();
        assertThat(lastEventType()).isEqualTo(TransferEvent.SUBMITTED);

        // os R$ 60 estão reservados: só sobram R$ 40 para qualquer outro Pix, até para dentro do banco
        Account friend = accounts.save(Accounts.active("0.00"));
        TransferView internal = transfers.execute(UUID.randomUUID(),
                new TransferCommand(payer.getId(), friend.getPixKey(), new BigDecimal("50.00"), null)).transfer();
        assertThat(internal.rejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_FUNDS);
    }

    @Test
    void spiLiquidaDebitaOClienteECreditaAContaDeLiquidacao() {
        Account payer = accounts.save(Accounts.active("100.00"));
        TransferView pending = pixTo(payer, MARIA, "60.00").transfer();

        assertThat(spiAnswers(pending, SpiResult.SETTLED, null)).isTrue();

        TransferView done = transfers.find(pending.id(), payer.getId());
        assertThat(done.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(done.settledAt()).isNotNull();
        assertThat(balanceOf(payer.getId())).isEqualByComparingTo("40.00");
        assertThat(balanceOf(SettlementAccount.ID)).isEqualByComparingTo("60.00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_entries WHERE transfer_id = ?",
                Long.class, pending.id())).isEqualTo(2);
        assertThat(lastEventType()).isEqualTo(TransferEvent.COMPLETED);
        // no extrato, quem aparece é a Maria, não a conta de liquidação
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transfers WHERE status = 'PENDING'", Long.class)).isZero();
    }

    @Test
    void spiRecusaLiberaAReservaSemMexerNoSaldo() {
        Account payer = accounts.save(Accounts.active("100.00"));
        TransferView pending = pixTo(payer, "encerrada@bancoexemplo.dev", "60.00").transfer();

        spiAnswers(pending, SpiResult.REJECTED, "AC03");

        TransferView refused = transfers.find(pending.id(), payer.getId());
        assertThat(refused.status()).isEqualTo(TransferStatus.REJECTED);
        assertThat(refused.rejectionReason()).isEqualTo(RejectionReason.RECEIVER_REJECTED);
        assertThat(balanceOf(payer.getId())).isEqualByComparingTo("100.00");
        assertThat(lastEventType()).isEqualTo(TransferEvent.REJECTED);
        // reserva liberada: os R$ 100 voltam a estar disponíveis
        assertThat(pixTo(payer, MARIA, "100.00").transfer().status()).isEqualTo(TransferStatus.PENDING);
    }

    @Test
    void respostaRepetidaDoSpiNaoLiquidaDuasVezes() {
        Account payer = accounts.save(Accounts.active("100.00"));
        TransferView pending = pixTo(payer, MARIA, "30.00").transfer();

        assertThat(spiAnswers(pending, SpiResult.SETTLED, null)).isTrue();
        assertThat(spiAnswers(pending, SpiResult.SETTLED, null)).isFalse();     // o Kafka entregou de novo
        assertThat(spiAnswers(pending, SpiResult.REJECTED, "AC03")).isFalse();  // e uma resposta atrasada

        assertThat(balanceOf(payer.getId())).isEqualByComparingTo("70.00");
        assertThat(transfers.find(pending.id(), payer.getId()).status()).isEqualTo(TransferStatus.COMPLETED);
    }

    @Test
    void recusaPorSaldoNemChegaAoSpi() {
        Account payer = accounts.save(Accounts.active("10.00"));

        TransferView refused = pixTo(payer, MARIA, "60.00").transfer();

        assertThat(refused.status()).isEqualTo(TransferStatus.REJECTED);
        assertThat(refused.rejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_FUNDS);
        assertThat(lastEventType()).isEqualTo(TransferEvent.REJECTED);
    }
}
