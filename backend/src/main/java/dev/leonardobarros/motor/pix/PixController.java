package dev.leonardobarros.motor.pix;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.leonardobarros.motor.auth.CurrentAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/pix/keys")
@Tag(name = "Pix")
@SecurityRequirement(name = "bearer")
class PixController {

    private final PixKeyService service;

    PixController(PixKeyService service) {
        this.service = service;
    }

    @GetMapping("/{pixKey}")
    @Operation(summary = "Consulta o dono de uma chave Pix antes de transferir")
    PixKeyView lookup(@PathVariable String pixKey, @CurrentAccount UUID accountId) {
        return service.lookup(pixKey, accountId);
    }
}
