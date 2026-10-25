package dev.leonardobarros.motor.transfer;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * As métricas que um time de operação de Pix olha primeiro:
 * <ul>
 *   <li>{@code pix.transferencias} por tipo, status e motivo da recusa: um salto de
 *       {@code LIMIT_EXCEEDED} ou {@code INSUFFICIENT_FUNDS} aparece no painel antes do SAC;</li>
 *   <li>{@code pix.idempotencia.replays}: muitos reenvios indicam rede ruim ou app com bug;</li>
 *   <li>{@code pix.lock.espera}: quanto as transações esperam pela trava das contas, o
 *       primeiro sinal de conta "quente" (uma loja recebendo milhares de Pix).</li>
 * </ul>
 */
@Component
public class TransferMetrics {

    private final MeterRegistry registry;
    private final Counter replays;
    private final Timer lockWait;

    public TransferMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.replays = Counter.builder("pix.idempotencia.replays")
                .description("Pedidos repetidos com a mesma Idempotency-Key, respondidos sem processar de novo")
                .register(registry);
        this.lockWait = Timer.builder("pix.lock.espera")
                .description("Tempo até obter a trava (SELECT ... FOR UPDATE) das duas contas")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    void processed(TransferView transfer) {
        Counter.builder("pix.transferencias")
                .tag("tipo", transfer.kind().name())
                .tag("status", transfer.status().name())
                .tag("motivo", transfer.rejectionReason() == null ? "nenhum" : transfer.rejectionReason().name())
                .register(registry)
                .increment();
    }

    void replayed() {
        replays.increment();
    }

    <T> T timeLockWait(Supplier<T> lock) {
        return lockWait.record(lock);
    }
}
