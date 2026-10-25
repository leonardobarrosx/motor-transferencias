package dev.leonardobarros.motor.transfer;

import static org.hamcrest.Matchers.endsWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import dev.leonardobarros.motor.auth.TokenService;
import dev.leonardobarros.motor.config.SecurityConfig;
import dev.leonardobarros.motor.shared.web.Headers;

@WebMvcTest(TransferController.class)
@Import(SecurityConfig.class)
class TransferControllerTest {

    private static final UUID KEY = UUID.fromString("7b1e6f0e-6c43-4d3a-9d43-3f5f1c2a9b10");
    private static final UUID SOURCE = UUID.fromString("0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1001");

    @Autowired MockMvc mvc;
    @MockitoBean TransferService service;

    /** Um JWT válido da conta SOURCE, como o resource server entregaria ao controller. */
    private static RequestPostProcessor loggedIn() {
        return jwt().jwt(token -> token.claim(TokenService.ACCOUNT_CLAIM, SOURCE.toString()));
    }

    private static MockHttpServletRequestBuilder transfer(String body) {
        return post("/api/v1/transfers")
                .with(loggedIn())
                .header(Headers.IDEMPOTENCY_KEY, KEY.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static String body(String amount) {
        return """
                {"targetPixKey": "ana@motor.dev", "amount": %s, "description": "aluguel"}
                """.formatted(amount);
    }

    private static TransferView view(TransferStatus status, RejectionReason reason) {
        UUID id = UUID.fromString("5d2c3b4a-0000-4000-8000-000000000001");
        return new TransferView(id, "E99999999202610211200ABCDEFGHIJK", status, reason, new BigDecimal("150.00"),
                "aluguel",
                new TransferView.Party(SOURCE, "Leonardo Barros", "leonardo@motor.dev"),
                new TransferView.Party(UUID.randomUUID(), "Ana Souza", "ana@motor.dev"),
                Instant.parse("2026-10-21T12:00:00Z"));
    }

    @Test
    void withoutTokenIs401() throws Exception {
        mvc.perform(post("/api/v1/transfers")
                        .header(Headers.IDEMPOTENCY_KEY, KEY.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("150.00")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verify(service, never()).execute(any(), any());
    }

    @Test
    void sourceAccountAlwaysComesFromTheToken() throws Exception {
        when(service.execute(eq(KEY), any())).thenReturn(new TransferResult(view(TransferStatus.COMPLETED, null), false));
        // mesmo que alguém mande um sourceAccountId no corpo, ele é ignorado
        String forged = """
                {"sourceAccountId": "%s", "targetPixKey": "ana@motor.dev", "amount": 10}
                """.formatted(UUID.randomUUID());

        mvc.perform(transfer(forged)).andExpect(status().isCreated());

        ArgumentCaptor<TransferCommand> command = ArgumentCaptor.forClass(TransferCommand.class);
        verify(service).execute(eq(KEY), command.capture());
        assertThat(command.getValue().sourceAccountId()).isEqualTo(SOURCE);
    }

    @Test
    void receiptIsLookedUpWithinTheLoggedAccount() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.find(id, SOURCE)).thenThrow(new TransferNotFoundException());

        mvc.perform(get("/api/v1/transfers/{id}", id).with(loggedIn()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSFER_NOT_FOUND"));
    }

    @Test
    void newTransferAnswers201WithLocation() throws Exception {
        when(service.execute(eq(KEY), any())).thenReturn(new TransferResult(view(TransferStatus.COMPLETED, null), false));

        mvc.perform(transfer(body("150.00")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/transfers/5d2c3b4a-0000-4000-8000-000000000001")))
                .andExpect(header().string(Headers.IDEMPOTENT_REPLAYED, "false"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value("150.00"))
                .andExpect(jsonPath("$.payee.holderName").value("Ana Souza"));
    }

    @Test
    void replayAnswers200AndSaysSo() throws Exception {
        when(service.execute(eq(KEY), any())).thenReturn(new TransferResult(view(TransferStatus.COMPLETED, null), true));

        mvc.perform(transfer(body("150.00")))
                .andExpect(status().isOk())
                .andExpect(header().string(Headers.IDEMPOTENT_REPLAYED, "true"))
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void businessRejectionIsAProblemDetail() throws Exception {
        when(service.execute(eq(KEY), any()))
                .thenReturn(new TransferResult(view(TransferStatus.REJECTED, RejectionReason.INSUFFICIENT_FUNDS), false));

        mvc.perform(transfer(body("150.00")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("TRANSFER_REJECTED"))
                .andExpect(jsonPath("$.reason").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.endToEndId").value("E99999999202610211200ABCDEFGHIJK"));
    }

    @Test
    void reusedKeyWithDifferentPayloadIs422() throws Exception {
        when(service.execute(eq(KEY), any())).thenThrow(new IdempotencyKeyReuseException(KEY));

        mvc.perform(transfer(body("150.00")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"))
                .andExpect(jsonPath("$.idempotencyKey").value(KEY.toString()));
    }

    @Test
    void lockTimeoutIs409AndRetryable() throws Exception {
        when(service.execute(eq(KEY), any())).thenThrow(new PessimisticLockingFailureException("lock_timeout"));

        mvc.perform(transfer(body("150.00")))
                .andExpect(status().isConflict())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.code").value("ACCOUNT_BUSY"));
    }

    @Test
    void acceptsTheAmountAsADecimalString() throws Exception {
        when(service.execute(eq(KEY), any())).thenReturn(new TransferResult(view(TransferStatus.COMPLETED, null), false));

        mvc.perform(transfer(body("\"150.00\""))).andExpect(status().isCreated());
    }

    @Test
    void idempotencyKeyIsMandatory() throws Exception {
        mvc.perform(post("/api/v1/transfers").with(loggedIn()).contentType(MediaType.APPLICATION_JSON).content(body("150.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
        verify(service, never()).execute(any(), any());
    }

    @Test
    void idempotencyKeyMustBeAUuid() throws Exception {
        mvc.perform(post("/api/v1/transfers")
                        .with(loggedIn())
                        .header(Headers.IDEMPOTENCY_KEY, "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("150.00")))
                .andExpect(status().isBadRequest());
        verify(service, never()).execute(any(), any());
    }

    @Test
    void rejectsZeroNegativeAndFractionsOfCentsBeforeReachingTheDomain() throws Exception {
        for (String amount : new String[] {"0", "-10", "10.001"}) {
            mvc.perform(transfer(body(amount)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("amount"));
        }
        verify(service, never()).execute(any(), any());
    }
}
