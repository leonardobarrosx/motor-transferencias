package dev.leonardobarros.motor.transfer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.transaction.annotation.Transactional;

import dev.leonardobarros.motor.account.Account;
import dev.leonardobarros.motor.account.AccountNotFoundException;
import dev.leonardobarros.motor.account.AccountRepository;

/**
 * Janelas de limite do Pix no horário de Brasília: diurna de 06h às 20h e noturna
 * de 20h às 06h do dia seguinte. A noturna atravessa a meia-noite: às 02h, a
 * janela começou às 20h de ONTEM.
 */
@Component
public class PixLimits {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    static final LocalTime DAY_STARTS = LocalTime.of(6, 0);
    static final LocalTime NIGHT_STARTS = LocalTime.of(20, 0);

    public enum Period { DAYTIME, NIGHTTIME }

    public record Window(Period period, Instant start, Instant end) {
    }

    public record Snapshot(Period period, Instant windowStart, Instant windowEnd,
                           @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal limit,
                           @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal used,
                           @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal available,
                           @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal daytimeLimit,
                           @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal nighttimeLimit) {
    }

    private final TransferRepository transfers;
    private final AccountRepository accounts;
    private final Clock clock;

    PixLimits(TransferRepository transfers, AccountRepository accounts, Clock clock) {
        this.transfers = transfers;
        this.accounts = accounts;
        this.clock = clock;
    }

    static Window windowAt(Instant now) {
        ZonedDateTime local = now.atZone(BRASILIA);
        LocalDate today = local.toLocalDate();
        LocalTime time = local.toLocalTime();
        if (!time.isBefore(DAY_STARTS) && time.isBefore(NIGHT_STARTS)) {
            return new Window(Period.DAYTIME, at(today, DAY_STARTS), at(today, NIGHT_STARTS));
        }
        LocalDate nightStarted = time.isBefore(DAY_STARTS) ? today.minusDays(1) : today;
        return new Window(Period.NIGHTTIME, at(nightStarted, NIGHT_STARTS), at(nightStarted.plusDays(1), DAY_STARTS));
    }

    private static Instant at(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(BRASILIA).toInstant();
    }

    static BigDecimal limitFor(Account account, Period period) {
        return period == Period.DAYTIME ? account.getDaytimePixLimit() : account.getNighttimePixLimit();
    }

    /**
     * Chamado com a conta pagadora JÁ travada: duas transferências simultâneas da
     * mesma conta não conseguem ler o mesmo "usado" e estourar o limite juntas.
     */
    boolean wouldExceed(Account payer, BigDecimal amount, Instant now) {
        Window window = windowAt(now);
        BigDecimal used = transfers.sumPixSentSince(payer.getId(), window.start());
        return used.add(amount).compareTo(limitFor(payer, window.period())) > 0;
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot(UUID accountId) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        Window window = windowAt(clock.instant());
        BigDecimal limit = limitFor(account, window.period());
        BigDecimal used = transfers.sumPixSentSince(accountId, window.start());
        BigDecimal available = limit.subtract(used).max(BigDecimal.ZERO.setScale(2));
        return new Snapshot(window.period(), window.start(), window.end(), limit, used, available,
                account.getDaytimePixLimit(), account.getNighttimePixLimit());
    }
}
