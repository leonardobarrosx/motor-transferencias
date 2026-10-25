package dev.leonardobarros.motor.account;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.leonardobarros.motor.auth.CurrentAccount;
import dev.leonardobarros.motor.ledger.EntryType;
import dev.leonardobarros.motor.ledger.StatementLine;
import dev.leonardobarros.motor.shared.web.PageResponse;
import dev.leonardobarros.motor.transfer.PixLimits;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Tudo aqui é da conta de quem está logado: não existe rota que receba o id de outra conta. */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Minha conta")
@SecurityRequirement(name = "bearer")
class MeController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AccountService service;
    private final PixLimits limits;

    MeController(AccountService service, PixLimits limits) {
        this.service = service;
        this.limits = limits;
    }

    @GetMapping
    @Operation(summary = "Saldo, dados da conta e movimento do mês")
    AccountView me(@CurrentAccount UUID accountId) {
        return service.get(accountId);
    }

    @GetMapping("/statement")
    @Operation(summary = "Extrato paginado, do mais recente para o mais antigo; type filtra entradas ou saídas")
    PageResponse<StatementLine> statement(@CurrentAccount UUID accountId,
                                          @RequestParam(required = false) EntryType type,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return PageResponse.from(service.statement(accountId, type, pageable), line -> line);
    }

    @GetMapping("/limits")
    @Operation(summary = "Limite Pix do período atual (diurno ou noturno): quanto já foi usado e quanto resta")
    PixLimits.Snapshot limits(@CurrentAccount UUID accountId) {
        return limits.snapshot(accountId);
    }

    @GetMapping("/contacts")
    @Operation(summary = "Contatos Pix recentes: com quem a conta transacionou por último")
    List<ContactView> contacts(@CurrentAccount UUID accountId) {
        return service.recentContacts(accountId);
    }
}
