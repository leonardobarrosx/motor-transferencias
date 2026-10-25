package dev.leonardobarros.motor.auth;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import dev.leonardobarros.motor.shared.web.Headers;
import dev.leonardobarros.motor.support.PostgresContainerConfig;

/**
 * A API de ponta a ponta, pelo HTTP, com o Spring Security de verdade e as
 * contas de demonstração: login, consulta, transferência e as fronteiras de acesso.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import(PostgresContainerConfig.class)
class ApiSecurityIT {

    @Autowired MockMvc mvc;

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"motor123\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    @Test
    void fullJourneyAndAccessBoundaries() throws Exception {
        String leonardo = login("leonardo@motor.dev");

        mvc.perform(get("/api/v1/me").header("Authorization", leonardo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holderName").value("Leonardo Barros"));

        mvc.perform(get("/api/v1/pix/keys/{key}", "ana@motor.dev").header("Authorization", leonardo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holderName").value("Ana Souza"))
                .andExpect(jsonPath("$.document").value("***.982.247-**"));

        String created = mvc.perform(post("/api/v1/transfers")
                        .header("Authorization", leonardo)
                        .header(Headers.IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetPixKey\": \"ana@motor.dev\", \"amount\": \"12.34\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String transferId = JsonPath.read(created, "$.id");

        mvc.perform(get("/api/v1/me/statement").param("type", "DEBIT").header("Authorization", leonardo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].transferId").value(transferId));

        mvc.perform(get("/api/v1/me/contacts").header("Authorization", leonardo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].holderName", hasItem("Ana Souza")));

        // quem recebeu vê o comprovante; um terceiro recebe 404, como se não existisse
        mvc.perform(get("/api/v1/transfers/{id}", transferId).header("Authorization", login("ana@motor.dev")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/transfers/{id}", transferId).header("Authorization", login("carlos@motor.dev")))
                .andExpect(status().isNotFound());
    }

    @Test
    void protectedRoutesNeedAValidToken() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        // troca o account_id do payload pelo da Ana e mantém a assinatura original
        String[] parts = login("leonardo@motor.dev").substring("Bearer ".length()).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1001", "0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1002");
        String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordAndUnknownEmailLookTheSame() throws Exception {
        for (String body : new String[] {
                "{\"email\": \"leonardo@motor.dev\", \"password\": \"errada\"}",
                "{\"email\": \"ninguem@motor.dev\", \"password\": \"motor123\"}"}) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }

    @Test
    void healthAndDocsStayPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
