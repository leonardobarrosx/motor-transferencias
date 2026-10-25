package dev.leonardobarros.motor.transfer;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import dev.leonardobarros.motor.auth.CurrentAccount;
import dev.leonardobarros.motor.shared.error.Problems;
import dev.leonardobarros.motor.shared.web.Headers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transferências")
@SecurityRequirement(name = "bearer")
class TransferController {

    private final TransferService service;

    TransferController(TransferService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Realiza uma transferência Pix entre contas",
            description = """
                    201: transferência concluída. 200: repetição de um pedido já concluído.
                    422: recusada por regra de negócio (a recusa também é idempotente).
                    O header Idempotent-Replayed indica se a resposta é uma repetição.""")
    ResponseEntity<?> create(
            @Parameter(description = "UUID gerado pelo cliente por intenção de transferir; reenvie o mesmo em caso de falha de rede.")
            @RequestHeader(Headers.IDEMPOTENCY_KEY) UUID idempotencyKey,
            @Valid @RequestBody TransferRequest request,
            @CurrentAccount UUID accountId) {

        return respond(service.execute(idempotencyKey, request.toCommand(accountId)));
    }

    @PostMapping("/{id}/refunds")
    @Operation(summary = "Devolve, total ou parcialmente, um Pix recebido",
            description = "Só quem recebeu pode devolver, até o valor original, em até 90 dias. Idempotente como o Pix.")
    ResponseEntity<?> refund(
            @PathVariable UUID id,
            @RequestHeader(Headers.IDEMPOTENCY_KEY) UUID idempotencyKey,
            @Valid @RequestBody RefundRequest request,
            @CurrentAccount UUID accountId) {
        return respond(service.refund(idempotencyKey, request.toCommand(accountId, id)));
    }

    private ResponseEntity<?> respond(TransferResult result) {
        TransferView transfer = result.transfer();
        String replayed = String.valueOf(result.replayed());

        return switch (transfer.status()) {
            case COMPLETED -> (result.replayed()
                    ? ResponseEntity.ok()
                    : ResponseEntity.created(locationOf(transfer.id())))
                    .header(Headers.IDEMPOTENT_REPLAYED, replayed)
                    .body(transfer);
            case REJECTED -> ResponseEntity.unprocessableContent()
                    .header(Headers.IDEMPOTENT_REPLAYED, replayed)
                    .body(rejection(transfer));
        };
    }

    @GetMapping("/{id}")
    @Operation(summary = "Comprovante de uma transferência que a conta enviou ou recebeu")
    TransferView get(@PathVariable UUID id, @CurrentAccount UUID accountId) {
        return service.find(id, accountId);
    }

    private static ProblemDetail rejection(TransferView transfer) {
        ProblemDetail problem = Problems.of(HttpStatus.UNPROCESSABLE_CONTENT, "TRANSFER_REJECTED",
                transfer.rejectionReason().message());
        problem.setProperty("reason", transfer.rejectionReason());
        problem.setProperty("transferId", transfer.id());
        problem.setProperty("endToEndId", transfer.endToEndId());
        return problem;
    }

    private static URI locationOf(UUID id) {
        // a partir da raiz: uma devolução é criada em /transfers/{original}/refunds, mas mora em /transfers/{id}
        return ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/v1/transfers/{id}").buildAndExpand(id).toUri();
    }
}
