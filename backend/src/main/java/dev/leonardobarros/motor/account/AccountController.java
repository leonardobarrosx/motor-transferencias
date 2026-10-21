package dev.leonardobarros.motor.account;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.leonardobarros.motor.ledger.StatementLine;
import dev.leonardobarros.motor.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Contas")
class AccountController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AccountService service;

    AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista as contas do simulador")
    List<AccountView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Saldo, dados da conta e movimento do mês")
    AccountView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/statement")
    @Operation(summary = "Extrato paginado, do lançamento mais recente para o mais antigo")
    PageResponse<StatementLine> statement(@PathVariable UUID id,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return PageResponse.from(service.statement(id, pageable), line -> line);
    }
}
